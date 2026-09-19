package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /curse} command; also reachable as {@code /mxt curse}. Reading the executing player's own curses needs
 * no permission, while changing anybody's - applying, removing or cleansing - keeps its operator requirement in
 * both positions.
 * <p>
 * The command is an administrator's mirror of the same transactions content uses: it goes through
 * {@link CurseService}, so a curse applied here obeys its own condition and stacking, a cure cleanses under the
 * same reason an antidote does, and a removal reports its reason like any other.
 */
public final class CurseCommand {
    /**
     * What the command records as the source of what it applies, in the same shape the other modules use.
     */
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "command");

    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("curse")
            .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayer()))
            .then(literal("list")
                    .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayer()))
                    .then(argument("target", EntityArgument.entity())
                            .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(literal("apply").requires(CurseCommand::mayChange)
                    .then(argument("targets", EntityArgument.entities())
                            .then(argument("curse", IdentifierArgument.id())
                                    .suggests(CurseCommand::suggestCurses)
                                    .executes(ctx -> apply(ctx, 1, OptionalLong.empty()))
                                    .then(argument("stacks", IntegerArgumentType.integer(1, 256))
                                            .executes(ctx -> apply(ctx, IntegerArgumentType.getInteger(ctx, "stacks"), OptionalLong.empty()))
                                            .then(argument("duration_ticks", LongArgumentType.longArg(1L))
                                                    .executes(ctx -> apply(ctx,
                                                            IntegerArgumentType.getInteger(ctx, "stacks"),
                                                            OptionalLong.of(LongArgumentType.getLong(ctx, "duration_ticks")))))))))
            .then(literal("remove").requires(CurseCommand::mayChange)
                    .then(argument("targets", EntityArgument.entities())
                            .then(argument("curse", IdentifierArgument.id())
                                    .suggests(CurseCommand::suggestCurses)
                                    .executes(CurseCommand::remove))))
            .then(literal("cleanse").requires(CurseCommand::mayChange)
                    .then(argument("targets", EntityArgument.entities())
                            .then(argument("tag", IdentifierArgument.id())
                                    .executes(CurseCommand::cleanse))));

    private CurseCommand() {
    }

    private static boolean mayChange(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static CompletableFuture<Suggestions> suggestCurses(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(MxtDatapackRegistries
                .holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.CURSE)
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder);
    }

    /**
     * Prints one holder's curses: name, stacks, and either the time left or the fact that there is none.
     */
    private static int list(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        Map<Holder<Curse>, State> instances = target.getData(MxtAttachments.CURSE_HOLDER).instances();
        source.sendSuccess(() -> Component.translatable("command.mxt.curse.list.header", target.getDisplayName()), false);
        if (instances.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.curse.list.empty"), false);
            return 1;
        }
        long gameTime = target.level().getGameTime();
        for (Map.Entry<Holder<Curse>, State> entry : instances.entrySet()) {
            Component name = DefinitionText.name(entry.getKey(), "curse");
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
        Identifier id = IdentifierArgument.getId(ctx, "curse");
        // The raw entry, not the enabled one: a disabled definition must reach the service so it can answer
        // DISABLED, and a definition that is gone entirely can still be held and therefore still be removed.
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Holder<Curse> curse = resolve(targets, id).orElse(null);
        if (curse == null) {
            source.sendFailure(Component.translatable("command.mxt.curse.unknown", id.toString()));
            return 0;
        }
        Optional<Long> duration = durationTicks.isPresent() ? Optional.of(durationTicks.getAsLong()) : Optional.empty();
        int applied = 0;
        for (Entity target : targets) {
            CurseService.ApplyResult result = CurseService.applyWithDuration(target, curse, stacks,
                    target.level().getGameTime(), FormulaContext.of(target), SOURCE, duration);
            if (result.applied()) {
                applied++;
                source.sendSuccess(() -> Component.translatable("command.mxt.curse.applied",
                        DefinitionText.name(curse, "curse"), stacks, target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.curse.apply_failed",
                        DefinitionText.name(curse, "curse"), target.getDisplayName(), String.valueOf(result.failure())));
            }
        }
        return applied;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "curse");
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        // A definition that is no longer loaded cannot be looked up, but it can still be held; finding it on a
        // holder is the only way such a curse can be taken off at all.
        Holder<Curse> curse = resolve(targets, id).orElse(null);
        if (curse == null) {
            source.sendFailure(Component.translatable("command.mxt.curse.unknown", id.toString()));
            return 0;
        }
        Holder<Curse> resolved = curse;
        int removed = 0;
        for (Entity target : targets) {
            if (CurseService.remove(target, resolved, Reason.EXPLICIT, target.level().getGameTime()).isPresent()) {
                removed++;
                source.sendSuccess(() -> Component.translatable("command.mxt.curse.removed",
                        DefinitionText.name(resolved, "curse"), target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.curse.remove_failed",
                        target.getDisplayName(), DefinitionText.name(resolved, "curse")));
            }
        }
        return removed;
    }

    /**
     * Removes every held curse carrying the requested tag: the console's mirror of an antidote.
     */
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

    /**
     * Resolves the definition the command is about: the raw registry entry, which even a disabled definition
     * still has, or failing that an instance that is already held, which is the only trace a deleted definition
     * leaves behind.
     */
    private static Optional<Holder<Curse>> resolve(Collection<? extends Entity> targets, Identifier id) {
        Optional<Holder<Curse>> registered = MxtDatapackRegistries.rawHolder(MxtResourceKeys.CURSE, id)
                .map(holder -> (Holder<Curse>) holder);
        return registered.isPresent() ? registered : heldByName(targets, id);
    }

    private static Optional<Holder<Curse>> heldByName(Collection<? extends Entity> targets, Identifier id) {
        for (Entity target : targets) {
            for (Holder<Curse> curse : target.getData(MxtAttachments.CURSE_HOLDER).instances().keySet())
                if (HolderHelper.id(curse).equals(id)) return Optional.of(curse);
        }
        return Optional.empty();
    }
}
