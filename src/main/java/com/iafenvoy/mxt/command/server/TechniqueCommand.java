package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationGrantService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHold;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueItemService;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService.Failure;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.Map.Entry;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /technique} command; also reachable as {@code /mxt technique}. It handles stored technique
 * references the current data pack no longer provides: {@code CollectionCodecs.list} drops undecodable elements,
 * so such a reference survives but fails quietly ({@code Ignoring invalid list element} in the log is the tell).
 */
public final class TechniqueCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("technique")
                .then(literal("repair")
                        .requires(ServerCommandManager::mayChange)
                        .executes(ctx -> repair(ctx.getSource(), false))
                        .then(literal("dry-run").executes(ctx -> repair(ctx.getSource(), true))))
                // IdentifierArgument, not ResourceArgument: the id named here is exactly the one that no longer resolves.
                .then(literal("drop")
                        .requires(ServerCommandManager::mayChange)
                        .then(argument("id", IdentifierArgument.id())
                                .executes(ctx -> drop(ctx.getSource(), IdentifierArgument.getId(ctx, "id")))))
                .then(literal("diagnose").executes(ctx -> diagnose(ctx.getSource())));
    }

    private static int diagnose(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.technique.diagnose.empty_hand"));
            return 0;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.item", itemId.toString()), false);

        Optional<TechniqueBinding> binding = ItemBindingService.technique(stack);
        if (binding.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.no_binding"), false);
            return 1;
        }
        TechniqueBinding value = binding.orElseThrow();
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.binding",
                value.technique().unwrapKey().map(key -> key.identifier().toString()).orElse("?"),
                value.learnTime(), value.holdAnimation().getSerializedName()), false);

        // This gate cancels Start and Tick, so a refusal here is what a pose that appears and then aborts looks like.
        Optional<Failure> refusal = ItemQualityService.check(player, stack);
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.gate", refusal.map(Enum::name).orElse("OK")), false);

        SpiritIdentityAttachment spirit = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        boolean known = spirit.learnedTechniques().contains(value.technique());
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.known",
                known ? "yes" : "no", spirit.learnedTechniques().size()), false);

        boolean condition = value.technique().value().learnCondition()
                .test(player, FormulaContext.of(player));
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.condition",
                condition ? "PASS" : "FAIL"), false);

        boolean cooldown = player.getCooldowns().isOnCooldown(stack);
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.cooldown",
                cooldown ? "YES" : "no"), false);

        // What the hold module resolves on this side: empty here means no hold can start whatever the pack says.
        boolean recognised = HoldLookup.hold(stack) instanceof TechniqueHold;
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.hold",
                recognised ? "YES" : "no"), false);

        // A hold ends either by running its course or by being let go early; the ticks left is what tells them apart.
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.diagnose.ending",
                TechniqueItemService.lastEnding(player).orElse("(none seen yet)")), false);
        return 1;
    }

    private static int repair(CommandSourceStack source, boolean dryRun) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        List<Identifier> removed = new ArrayList<>();

        List<Holder<Technique>> techniques = prune(identity.learnedTechniques(), removed);
        Map<Holder<Technique>, Holder<SkillStage>> stages = pruneStages(identity.techniqueStages(), removed);

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

    // The sweep cannot reach a reference that is already gone, and naming the entry also undoes a mistaken grant.
    private static int drop(CommandSourceStack source, Identifier id) {
        ServerPlayer player = source.getPlayerOrException();
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        int before = identity.learnedTechniques().size() + identity.techniqueStages().size();

        List<Holder<Technique>> techniques = new ArrayList<>();
        for (Holder<Technique> technique : identity.learnedTechniques())
            if (!HolderHelper.id(technique).equals(id)) techniques.add(technique);

        Map<Holder<Technique>, Holder<SkillStage>> stages = new LinkedHashMap<>();
        for (Entry<Holder<Technique>, Holder<SkillStage>> entry : identity.techniqueStages().entrySet())
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

    // Granted abilities, passive attributes and resource ceilings all derive from the definitions just dropped.
    private static void rebuild(ServerPlayer player, SpiritIdentityAttachment identity) {
        CultivationGrantService.recalculate(player, identity, player.getData(MxtAttachments.ABILITY_HOLDER));
    }

    // Package-visible so the server audit can exercise the sweep directly.
    public static List<Holder<Technique>> prune(List<Holder<Technique>> values, List<Identifier> removed) {
        List<Holder<Technique>> kept = new ArrayList<>(values.size());
        Set<Identifier> seen = new LinkedHashSet<>();
        for (Holder<Technique> technique : values) {
            Identifier id = HolderHelper.id(technique);
            if (!resolves(technique) || !seen.add(id)) {
                removed.add(id);
                continue;
            }
            kept.add(technique);
        }
        return kept;
    }

    public static Map<Holder<Technique>, Holder<SkillStage>> pruneStages(Map<Holder<Technique>, Holder<SkillStage>> values, List<Identifier> removed) {
        Map<Holder<Technique>, Holder<SkillStage>> kept = new LinkedHashMap<>();
        for (Entry<Holder<Technique>, Holder<SkillStage>> entry : values.entrySet()) {
            Holder<Technique> technique = entry.getKey();
            if (!resolves(technique) || !resolvesStage(entry.getValue())) {
                removed.add(HolderHelper.id(technique));
                continue;
            }
            kept.put(technique, entry.getValue());
        }
        return kept;
    }

    // Checked by id against the live registry: a removed entry has no value to read, and asking for one would
    // throw. Package-visible for the server audit, which cannot build a genuine unbound holder.
    public static boolean resolves(Holder<Technique> technique) {
        if (technique == null) return false;
        Identifier id = HolderHelper.id(technique);
        if (id.equals(HolderHelper.EMPTY)) return false;
        return MxtDatapackRegistries.get(MxtResourceKeys.TECHNIQUE, id).isPresent();
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
