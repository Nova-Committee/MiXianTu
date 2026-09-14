package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.runtime.item.ItemQualityService.Failure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Map.Entry;
import java.util.Optional;

import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHoldLookup;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueItemService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationGrantService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /technique} command; also reachable as {@code /mxt technique}.
 *
 * <p>Its subject is technique data that points at entries the current data pack no longer provides.
 * A stale reference is survivable, but it fails quietly in the wrong way: the lists decode through
 * {@code CollectionCodecs.list}, which is {@code AutoIgnoreListCodec} - it decodes element by element
 * and drops the ones that fail, logging a single warning. A removed technique therefore costs only
 * itself and the player keeps everything else; what they lose is that one technique, with no in-game
 * message saying why it went.</p>
 *
 * <p>{@code repair} makes that state explicit and tidy: it names the dead entries, removes them so the
 * stored data stops carrying references that can never resolve again, and rebuilds the attributes and
 * abilities derived from the techniques that remain. On healthy data it changes nothing.</p>
 *
 * <p>The warning line is the tell for whether this is the right tool at all. If the log never shows
 * {@code Ignoring invalid list element}, there are no stale references, and an empty panel or an inert
 * manual has some other cause entirely.</p>
 */
public final class TechniqueCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("technique")
            .then(literal("repair")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(ctx -> repair(ctx.getSource(), false))
                    .then(literal("dry-run").executes(ctx -> repair(ctx.getSource(), true))))
            .then(literal("drop")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(argument("id", IdentifierArgument.id())
                            .executes(ctx -> drop(ctx.getSource(), IdentifierArgument.getId(ctx, "id")))))
            .then(literal("diagnose").executes(ctx -> diagnose(ctx.getSource())));

    /**
     * Reports why the item in hand cannot be used, one gate at a time.
     *
     * <p>The gates that surround a use cycle are spread across a binding, a quality group and a learn
     * condition, and each can refuse independently. When an item simply does nothing, "which one said
     * no" is the whole question, and it is not answerable from outside the game.</p>
     */
    private static int diagnose(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.technique.diagnose.empty_hand"));
            return 0;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.item", itemId.toString()), false);

        // 1. Does the item carry a technique binding at all?
        Optional<TechniqueBinding> binding = ItemBindingService.technique(stack);
        if (binding.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.no_binding"), false);
            return 1;
        }
        TechniqueBinding value = binding.orElseThrow();
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.binding",
                value.technique().unwrapKey().map(key -> key.identifier().toString()).orElse("?"),
                value.learnTime(), value.holdAnimation().getSerializedName()), false);

        // 2. Does the item gate refuse it? This is the one that cancels Start and Tick, which is what a
        // pose that appears and then aborts looks like.
        Optional<Failure> refusal = ItemQualityService.check(player, stack);
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.gate", refusal.map(Enum::name).orElse("OK")), false);

        // 3. Is it already known, or blocked by an exclusive tag?
        SpiritIdentityAttachment spirit = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        boolean known = spirit.learnedTechniques().contains(value.technique());
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.known",
                known ? "yes" : "no", spirit.learnedTechniques().size()), false);

        // 4. Does the technique's own condition pass?
        boolean condition = value.technique().value().learnCondition()
                .test(player, FormulaContext.of(player));
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.condition",
                condition ? "PASS" : "FAIL"), false);

        // 5. Is the item on cooldown right now?
        boolean cooldown = player.getCooldowns().isOnCooldown(stack);
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.cooldown",
                cooldown ? "YES" : "no"), false);

        // 6. Does the server recognise this item as a hold manual at all? This is what the mixin reads,
        // and an empty answer here means no hold can start on this side no matter what the data pack says.
        boolean recognised = TechniqueHoldLookup.hold(stack) != null;
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.hold",
                recognised ? "YES" : "no"), false);

        // 7. How the last reading gesture ended. A hold has two endings - it ran its full course, or it
        // was let go early - and the ticks left at that moment is the only thing that tells them apart.
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.ending",
                TechniqueItemService.lastEnding(player).orElse("(none seen yet)")), false);
        return 1;
    }

    /**
     * Sweeps the attachment and removes every reference that no longer resolves.
     *
     * @param dryRun when true, reports what would be removed and changes nothing
     */
    private static int repair(CommandSourceStack source, boolean dryRun) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        List<Identifier> removed = new ArrayList<>();

        List<Holder<CultivationTechnique>> techniques = prune(identity.learnedTechniques(), removed);
        Map<Holder<CultivationTechnique>, Holder<SkillStage>> stages = pruneStages(identity.techniqueStages(), removed);

        if (!dryRun) {
            identity.setLearnedTechniques(techniques);
            identity.setTechniqueStages(stages);
        }

        int count = removed.size();
        if (count == 0) {
            source.sendSuccess(() -> Component.translatable("command.mxt.technique.repair.clean"), false);
            return 1;
        }
        if (!dryRun) rebuild(player, identity);
        String listed = join(removed);
        source.sendSuccess(() -> Component.translatable(dryRun
                ? "command.mxt.technique.repair.dry"
                : "command.mxt.technique.repair.done", count, listed), true);
        return count;
    }

    /**
     * Removes one named technique from the holder, whether or not it still resolves.
     *
     * <p>The sweep above cannot reach a reference that is already gone, and the sweep is also no use to
     * a player who simply wants a mistaken grant undone. Naming the entry directly covers both.</p>
     */
    private static int drop(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        int before = identity.learnedTechniques().size() + identity.techniqueStages().size();

        List<Holder<CultivationTechnique>> techniques = new ArrayList<>();
        for (Holder<CultivationTechnique> technique : identity.learnedTechniques())
            if (!HolderHelper.id(technique).equals(id)) techniques.add(technique);

        Map<Holder<CultivationTechnique>, Holder<SkillStage>> stages = new LinkedHashMap<>();
        for (Entry<Holder<CultivationTechnique>, Holder<SkillStage>> entry : identity.techniqueStages().entrySet())
            if (!HolderHelper.id(entry.getKey()).equals(id)) stages.put(entry.getKey(), entry.getValue());

        int after = techniques.size() + stages.size();
        if (before == after) {
            source.sendFailure(Component.translatable("command.mxt.technique.drop.absent", id.toString()));
            return 0;
        }
        identity.setLearnedTechniques(techniques);
        identity.setTechniqueStages(stages);
        rebuild(player, identity);
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.drop.done", id.toString()), true);
        return 1;
    }

    /**
     * Rebuilds the state derived from the technique list after it changed.
     *
     * <p>Removing a technique is not just a list edit: granted abilities, passive attributes and the
     * resource ceilings all came from the definitions that were just dropped, so they have to be
     * recomputed or the player keeps buffs from a technique they no longer hold.</p>
     */
    private static void rebuild(ServerPlayer player, SpiritIdentityAttachment identity) {
        CultivationGrantService.recalculate(player, identity, player.getData(MxtAttachments.ABILITY_HOLDER));
    }

    /**
     * Drops every stored technique that no longer resolves, keeping the rest in order.
     *
     * <p>Package-visible so the server audit can exercise the sweep directly: the command itself needs a
     * real player, and this is the half that decides what a repair actually does.</p>
     */
    public static List<Holder<CultivationTechnique>> prune(List<Holder<CultivationTechnique>> values, List<Identifier> removed) {
        List<Holder<CultivationTechnique>> kept = new ArrayList<>(values.size());
        Set<Identifier> seen = new LinkedHashSet<>();
        for (Holder<CultivationTechnique> technique : values) {
            Identifier id = HolderHelper.id(technique);
            if (!resolves(technique) || !seen.add(id)) {
                removed.add(id);
                continue;
            }
            kept.add(technique);
        }
        return kept;
    }

    public static Map<Holder<CultivationTechnique>, Holder<SkillStage>> pruneStages(Map<Holder<CultivationTechnique>, Holder<SkillStage>> values, List<Identifier> removed) {
        Map<Holder<CultivationTechnique>, Holder<SkillStage>> kept = new LinkedHashMap<>();
        for (Entry<Holder<CultivationTechnique>, Holder<SkillStage>> entry : values.entrySet()) {
            Holder<CultivationTechnique> technique = entry.getKey();
            if (!resolves(technique) || !resolvesStage(entry.getValue())) {
                removed.add(HolderHelper.id(technique));
                continue;
            }
            kept.put(technique, entry.getValue());
        }
        return kept;
    }

    /**
     * Whether a stored technique still resolves to an enabled definition.
     *
     * <p>The check is by id against the live registry rather than by reading the holder's value: a
     * reference whose entry has been removed from the data pack has no value to read, and asking for
     * one would throw instead of reporting the entry as stale.</p>
     *
     * <p>Package-visible for the server audit, which cannot build a genuine unbound holder.</p>
     */
    public static boolean resolves(Holder<CultivationTechnique> technique) {
        if (technique == null) return false;
        Identifier id = HolderHelper.id(technique);
        if (id.equals(HolderHelper.EMPTY)) return false;
        return MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATION_TECHNIQUE, id).isPresent();
    }

    public static boolean resolvesStage(Holder<SkillStage> stage) {
        if (stage == null) return false;
        Identifier id = HolderHelper.id(stage);
        if (id.equals(HolderHelper.EMPTY)) return false;
        return MxtDatapackRegistries.get(MxtResourceKeys.SKILL_STAGE, id).isPresent();
    }

    private static String join(List<Identifier> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (Identifier id : values) unique.add(id.toString());
        return String.join(", ", unique);
    }
}
