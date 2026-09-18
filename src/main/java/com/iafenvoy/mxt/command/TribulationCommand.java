package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.Failure;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.StartResult;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt tribulation} subtree: run a trial by hand instead of waiting for the breakthrough that would
 * have started it, and read back what a run is doing. Everything it does goes through the same
 * {@link TribulationService} the breakthrough path uses, so the gate, the validation and the timeline itself
 * behave exactly as they do in play; only the decision to start one is replaced.
 *
 * <p>The run belongs to an entity — the caller by default, or a named target, which is what lets a trial be
 * reproduced on a summon instead of only on a player. {@code status} reports the state slot in the spelling the
 * codec saves, which is the one form that shows exactly what the current entry has kept: the ticks an idle has
 * left, or nothing but the marker that the entry began.</p>
 */
public final class TribulationCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("tribulation")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(literal("start")
                    .then(argument("id", IdentifierArgument.id())
                            .suggests(TribulationCommand::suggestTribulations)
                            .executes(ctx -> start(ctx.getSource(), IdentifierArgument.getId(ctx, "id"), null))
                            .then(target(ctx -> start(ctx.getSource(), IdentifierArgument.getId(ctx, "id"),
                                    EntityArgument.getEntity(ctx, "target"))))))
            .then(literal("stop")
                    .executes(ctx -> stop(ctx.getSource(), null))
                    .then(target(ctx -> stop(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(literal("status")
                    .executes(ctx -> status(ctx.getSource(), null))
                    .then(target(ctx -> status(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))));

    private TribulationCommand() {
    }

    /**
     * The optional {@code target} node all three verbs share: without it the command works on the caller.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> target(Command<CommandSourceStack> verb) {
        return argument("target", EntityArgument.entity()).executes(verb);
    }

    private static CompletableFuture<Suggestions> suggestTribulations(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                MxtDatapackRegistries.holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.TRIBULATION)
                        .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder);
    }

    private static int start(CommandSourceStack source, Identifier id, @Nullable Entity target) {
        LivingEntity entity = target(source, target);
        if (entity == null) return 0;
        Reference<Tribulation> tribulation = MxtDatapackRegistries.holder(MxtResourceKeys.TRIBULATION, id).orElse(null);
        if (tribulation == null) {
            source.sendFailure(Component.translatable("command.mxt.tribulation.unknown", id.toString()));
            return 0;
        }
        TribulationAttachment data = entity.getData(MxtAttachments.TRIBULATION);
        StartResult result = TribulationService.start(entity, data, tribulation, entity.level().getGameTime(),
                FormulaContexts.forEntity(entity));
        if (!result.started()) {
            source.sendFailure(Component.translatable("command.mxt.tribulation.failed", DefinitionText.name(tribulation),
                    Component.translatable("command.mxt.tribulation.failure." + name(result.failure()))));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.tribulation.started", DefinitionText.name(tribulation),
                data.remaining(), entity.getDisplayName()), true);
        return 1;
    }

    private static int stop(CommandSourceStack source, @Nullable Entity target) {
        LivingEntity entity = target(source, target);
        if (entity == null) return 0;
        TribulationAttachment data = entity.getData(MxtAttachments.TRIBULATION);
        if (data.tribulation().isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.tribulation.none", entity.getDisplayName()));
            return 0;
        }
        Component running = DefinitionText.name(data.tribulation().get());
        // Clearing is the same thing a finished run does, so a stopped run leaves nothing behind to resume.
        data.clear();
        source.sendSuccess(() -> Component.translatable("command.mxt.tribulation.stopped", running, entity.getDisplayName()), true);
        return 1;
    }

    private static int status(CommandSourceStack source, @Nullable Entity target) {
        LivingEntity entity = target(source, target);
        if (entity == null) return 0;
        TribulationAttachment data = entity.getData(MxtAttachments.TRIBULATION);
        if (data.tribulation().isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.tribulation.none", entity.getDisplayName()));
            return 0;
        }
        Component state = data.state().map(TribulationCommand::describeState)
                .orElseGet(() -> Component.translatable("command.mxt.tribulation.state_not_begun"));
        source.sendSuccess(() -> Component.translatable("command.mxt.tribulation.status",
                DefinitionText.name(data.tribulation().get()), entity.getDisplayName(),
                data.consumed() + 1, data.remaining(), state), false);
        return 1;
    }

    /**
     * The entity the run belongs to: the named target, or the caller when there is none.
     */
    private static LivingEntity target(CommandSourceStack source, @Nullable Entity target) {
        Entity entity = target != null ? target : source.getPlayer();
        if (entity instanceof LivingEntity living) return living;
        source.sendFailure(Component.translatable(entity == null ? "command.mxt.requires_player" : "command.mxt.tribulation.not_living"));
        return null;
    }

    /**
     * The state slot in its saved spelling. The codec is the only thing that knows what a kind holds, so this
     * prints whatever the next save would have written instead of a shape the command would have to know.
     */
    private static Component describeState(DataStorage value) {
        return DataStorage.CODEC.encodeStart(JsonOps.INSTANCE, value).result()
                .<Component>map(json -> Component.literal(json.toString()))
                .orElseGet(() -> Component.literal(value.getClass().getSimpleName()));
    }

    private static String name(Failure failure) {
        return failure == null ? "unknown" : failure.name().toLowerCase(Locale.ROOT);
    }
}
