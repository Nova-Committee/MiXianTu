package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHold;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueItemService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
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
                // The same surgery under the name a player means: dropping a mistake, forgetting a technique.
                .then(literal("forget")
                        .requires(ServerCommandManager::mayChange)
                        .then(argument("id", IdentifierArgument.id())
                                .executes(ctx -> forget(ctx.getSource(), IdentifierArgument.getId(ctx, "id")))))
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
        Map<Identifier, Holder<Progression>> levels = pruneLevels(player.getData(MxtAttachments.PROGRESSION).levels(), removed);

        if (!dryRun) {
            identity.setLearnedTechniques(techniques);
            player.getData(MxtAttachments.PROGRESSION).setLevels(levels);
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

    // Both nodes are the same removal, kept apart only by what they are pointed at: "drop" is used against a
    // reference the pack no longer provides, "forget" is a player giving up a technique they still hold.
    private static int drop(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!TechniqueService.forget(player, player.getData(MxtAttachments.SPIRIT_IDENTITY), id).changed()) {
            source.sendFailure(Component.translatable("command.mxt.technique.drop.absent", id.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.drop.done", id.toString()), true);
        return 1;
    }

    private static int forget(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!TechniqueService.forget(player, player.getData(MxtAttachments.SPIRIT_IDENTITY), id).changed()) {
            source.sendFailure(Component.translatable("command.mxt.technique.forget.absent", id.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.technique.forget.done", id.toString()), true);
        return 1;
    }

    // Granted abilities, passive attributes and resource ceilings all derive from the definitions just dropped.
    private static void rebuild(ServerPlayer player, SpiritIdentityAttachment identity) {
        AbilityGrantService.recalculate(player);
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

    // Only the ids this sweep collected are dropped, so a progression owned by something else survives the
    // technique repair; a level whose definition is gone is stale for everyone and goes too.
    public static Map<Identifier, Holder<Progression>> pruneLevels(Map<Identifier, Holder<Progression>> values, List<Identifier> removed) {
        Map<Identifier, Holder<Progression>> kept = new LinkedHashMap<>();
        for (Entry<Identifier, Holder<Progression>> entry : values.entrySet()) {
            if (removed.contains(entry.getKey()) || !resolvesLevel(entry.getValue())) {
                removed.add(entry.getKey());
                continue;
            }
            kept.put(entry.getKey(), entry.getValue());
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

    public static boolean resolvesLevel(Holder<Progression> level) {
        if (level == null) return false;
        Identifier id = HolderHelper.id(level);
        if (id.equals(HolderHelper.EMPTY)) return false;
        return MxtDatapackRegistries.get(MxtResourceKeys.PROGRESSION, id).isPresent();
    }

    private static String join(List<Identifier> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (Identifier id : values) unique.add(id.toString());
        return String.join(", ", unique);
    }
}
