package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt tribulation} subtree: run a trial by hand instead of waiting for the breakthrough that would have
 * started it, and read back what a run is doing. It goes through the same {@link TribulationService} the
 * breakthrough path uses, so the gate, the validation and the timeline behave exactly as they do in play; only the
 * decision to start one is replaced. {@code status} prints the state slot in the spelling the codec saves.
 */
public final class TribulationCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("tribulation")
                .requires(ServerCommandManager::mayChange)
                .then(literal("start")
                        .then(argument("id", ResourceArgument.resource(context, MxtResourceKeys.TRIBULATION))
                                .executes(ctx -> start(ctx.getSource(), tribulation(ctx), null))
                                .then(target(ctx -> start(ctx.getSource(), tribulation(ctx), EntityArgument.getEntity(ctx, "target"))))))
                .then(literal("stop")
                        .executes(ctx -> stop(ctx.getSource(), null))
                        .then(target(ctx -> stop(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("status")
                        .executes(ctx -> status(ctx.getSource(), null))
                        .then(target(ctx -> status(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))));
    }

    private static Reference<Tribulation> tribulation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "id", MxtResourceKeys.TRIBULATION);
    }

    // Optional: without it the command works on the caller.
    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> target(Command<CommandSourceStack> verb) {
        return argument("target", EntityArgument.entity()).executes(verb);
    }

    private static int start(CommandSourceStack source, Reference<Tribulation> tribulation, @Nullable Entity target) {
        LivingEntity entity = target(source, target);
        if (entity == null) return 0;
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.TRIBULATION, tribulation)) {
            source.sendFailure(Component.translatable("command.mxt.tribulation.unknown", HolderHelper.id(tribulation).toString()));
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
        source.sendSuccess(() -> data.windup() > 0L
                ? Component.translatable("command.mxt.tribulation.started_windup", DefinitionText.name(tribulation),
                data.remaining(), entity.getDisplayName(), data.windup())
                : Component.translatable("command.mxt.tribulation.started", DefinitionText.name(tribulation),
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
        // A run that is still counting itself in has not begun: reporting a beat ordinal here would claim the
        // timeline is under way when nothing has been consumed yet.
        if (data.windup() > 0L) {
            source.sendSuccess(() -> Component.translatable("command.mxt.tribulation.status_windup",
                    DefinitionText.name(data.tribulation().get()), entity.getDisplayName(), data.windup()), false);
            return 1;
        }
        Component state = data.state().map(TribulationCommand::describeState)
                .orElseGet(() -> Component.translatable("command.mxt.tribulation.state_not_begun"));
        source.sendSuccess(() -> Component.translatable("command.mxt.tribulation.status",
                DefinitionText.name(data.tribulation().get()), entity.getDisplayName(),
                data.consumed() + 1, data.remaining(), state), false);
        return 1;
    }

    private static LivingEntity target(CommandSourceStack source, @Nullable Entity target) {
        Entity entity = target != null ? target : source.getPlayer();
        if (entity instanceof LivingEntity living) return living;
        source.sendFailure(Component.translatable(entity == null ? "command.mxt.requires_player" : "command.mxt.tribulation.not_living"));
        return null;
    }

    // The codec is the only thing that knows what a kind holds, so this prints whatever the next save would write.
    private static Component describeState(DataStorage value) {
        return DataStorage.CODEC.encodeStart(JsonOps.INSTANCE, value).result()
                .<Component>map(json -> Component.literal(json.toString()))
                .orElseGet(() -> Component.literal(value.getClass().getSimpleName()));
    }

    private static String name(Failure failure) {
        return failure == null ? "unknown" : failure.name().toLowerCase(Locale.ROOT);
    }
}
