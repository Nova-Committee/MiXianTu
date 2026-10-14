package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyResult;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Map.Entry;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /curse} command; also reachable as {@code /mxt curse}. Reading the executing player's own curses needs
 * no permission, while changing anybody's - applying, removing or cleansing - keeps its operator requirement in
 * both positions. It is an administrator's mirror of the transactions content uses, going through
 * {@link CurseService}, so a curse applied here obeys its own condition and stacking.
 */
public final class CurseCommand {
    // Recorded as the source of what the command applies, in the same shape the other modules use.
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "command");

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("curse")
                .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("apply").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("curse", ResourceArgument.resource(context, MxtResourceKeys.CURSE))
                                        .executes(ctx -> apply(ctx, 1, OptionalLong.empty()))
                                        .then(argument("stacks", IntegerArgumentType.integer(1, 256))
                                                .executes(ctx -> apply(ctx, IntegerArgumentType.getInteger(ctx, "stacks"), OptionalLong.empty()))
                                                .then(argument("duration_ticks", LongArgumentType.longArg(1L))
                                                        .executes(ctx -> apply(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "stacks"),
                                                                OptionalLong.of(LongArgumentType.getLong(ctx, "duration_ticks")))))))))
                // ResourceKeyArgument, not ResourceArgument: an instance whose definition is gone is still held, and
                // naming it here is the only way to take it off; the key never has to resolve.
                .then(literal("remove").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("curse", ResourceArgument.resource(context, MxtResourceKeys.CURSE))
                                        .executes(CurseCommand::remove))))
                .then(literal("cleanse").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("tag", IdentifierArgument.id())
                                        .executes(CurseCommand::cleanse))));
    }

    private static int list(CommandSourceStack source, @NotNull Entity target) {
        Map<Holder<Curse>, State> instances = target.getData(MxtAttachments.CURSE_HOLDER).instances();
        source.sendSuccess(() -> Component.translatable("command.mxt.curse.list.header", target.getDisplayName()), false);
        if (instances.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.curse.list.empty"), false);
            return 1;
        }
        long gameTime = target.level().getGameTime();
        for (Entry<Holder<Curse>, State> entry : instances.entrySet()) {
            Component name = DefinitionText.text(entry.getKey());
            int stacks = entry.getValue().stacks();
            Component life = entry.getValue().expiresAt() < 0L
                    ? Component.translatable("info.mxt.curse.permanent")
                    : Component.translatable("info.mxt.curse.remaining", Math.max(0L, entry.getValue().expiresAt() - gameTime));
            Component from = Component.literal(target.getData(MxtAttachments.CURSE_HOLDER).sources().of(entry.getKey()).stream()
                    .map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("-"));
            source.sendSuccess(() -> Component.translatable("command.mxt.curse.list.line", name, stacks, life, from), false);
        }
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, int stacks, OptionalLong durationTicks) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<Curse> curse = ResourceArgument.getResource(ctx, "curse", MxtResourceKeys.CURSE);
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Optional<Long> duration = durationTicks.isPresent() ? Optional.of(durationTicks.getAsLong()) : Optional.empty();
        int applied = 0;
        for (Entity target : targets) {
            ApplyResult result = CurseService.applyWithDuration(target, curse, stacks,
                    target.level().getGameTime(), FormulaContext.of(target), SOURCE, duration);
            if (result.applied()) {
                applied++;
                source.sendSuccess(() -> Component.translatable("command.mxt.curse.applied",
                        DefinitionText.text(curse), stacks, target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.curse.apply_failed",
                        DefinitionText.text(curse), target.getDisplayName(), String.valueOf(result.failure())));
            }
        }
        return applied;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Holder<Curse> curse = ResourceArgument.getResource(ctx, "curse", MxtResourceKeys.CURSE);
        int removed = 0;
        for (Entity target : targets) {
            if (CurseService.remove(target, curse, Reason.EXPLICIT, target.level().getGameTime()).isPresent()) {
                removed++;
                source.sendSuccess(() -> Component.translatable("command.mxt.curse.removed", DefinitionText.text(curse), target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.curse.remove_failed", target.getDisplayName(), DefinitionText.text(curse)));
            }
        }
        return removed;
    }

    private static int cleanse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        TagKey<Curse> tag = TagKey.create(MxtResourceKeys.CURSE, IdentifierArgument.getId(ctx, "tag"));
        int cleansed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            long gameTime = target.level().getGameTime();
            List<Holder<Curse>> matches = new ArrayList<>();
            for (Holder<Curse> curse : target.getData(MxtAttachments.CURSE_HOLDER).instances().keySet())
                if (curse.is(tag)) matches.add(curse);
            int removed = 0;
            for (Holder<Curse> curse : matches) {
                if (CurseService.remove(target, curse, Reason.CLEANSED, gameTime).isPresent()) removed++;
            }
            cleansed += removed;
            // A frozen instance is carried but not cleansable, so the report counts what actually left the
            // holder rather than what the tag matched.
            if (removed > 0) {
                int count = removed;
                source.sendSuccess(() -> Component.translatable("command.mxt.curse.cleansed", count, target.getDisplayName()), true);
            } else if (!matches.isEmpty()) {
                source.sendFailure(Component.translatable("command.mxt.curse.cleanse_frozen",
                        target.getDisplayName(), String.valueOf(matches.size())));
            }
        }
        return cleansed;
    }

    // The registry entry the pack loads now, or failing that an instance already held - the only trace a definition
    // the pack no longer provides leaves behind.

    private static Optional<Holder<Curse>> heldByName(Collection<? extends Entity> targets, Identifier id) {
        for (Entity target : targets) {
            for (Holder<Curse> curse : target.getData(MxtAttachments.CURSE_HOLDER).instances().keySet())
                if (HolderHelper.id(curse).equals(id)) return Optional.of(curse);
        }
        return Optional.empty();
    }
}
