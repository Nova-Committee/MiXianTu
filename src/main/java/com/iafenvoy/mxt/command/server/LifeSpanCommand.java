package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /lifespan} command; also reachable as {@code /mxt lifespan}. {@code get} prints the ledger of every
 * target, {@code set} rewrites both numbers, {@code add} extends or takes away life and {@code reincarnate} runs
 * the rebirth reset. Written in ticks, which is the unit the ledger really holds; the readout is the same one
 * the information panel shows.
 */
public final class LifeSpanCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("lifespan")
                .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayer()))
                .then(literal("get")
                        .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("targets", EntityArgument.entities()).requires(ServerCommandManager::mayChange)
                                .executes(ctx -> get(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(literal("set").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("ticks", LongArgumentType.longArg(0L))
                                        .executes(LifeSpanCommand::set))))
                .then(literal("add").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("ticks", LongArgumentType.longArg())
                                        .executes(LifeSpanCommand::add))))
                .then(literal("reincarnate").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .executes(LifeSpanCommand::reincarnate)));
    }

    private static int get(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.lifespan.get", target.getDisplayName(), read(target)), false);
        return 1;
    }

    private static int get(CommandSourceStack source, Iterable<? extends Entity> targets) {
        int shown = 0;
        for (Entity target : targets) {
            source.sendSuccess(() -> Component.translatable("command.mxt.lifespan.get", target.getDisplayName(), read(target)), false);
            shown++;
        }
        return shown;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return write(ctx, true);
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return write(ctx, false);
    }

    // Rebirth on demand. It is the same reset the expiry outcome runs, so the readout afterwards is the ledger
    // the next life starts from rather than the one that was spent; a listener may still refuse it.
    private static int reincarnate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int done = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.lifespan.not_living", target.getDisplayName()));
                continue;
            }
            LifeSpanService.Result result = LifeSpanService.reincarnate(living);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.lifespan.reincarnate_failed",
                        target.getDisplayName(), Component.translatable("command.mxt.lifespan.failure."
                                + result.failure().name().toLowerCase(Locale.ROOT))));
                continue;
            }
            done++;
            source.sendSuccess(() -> Component.translatable("command.mxt.lifespan.reincarnate",
                    target.getDisplayName(), read(target)), true);
        }
        return done;
    }

    private static int write(CommandContext<CommandSourceStack> ctx, boolean rewrite) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        long ticks = LongArgumentType.getLong(ctx, "ticks");
        int changed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.lifespan.not_living", target.getDisplayName()));
                continue;
            }
            LifeSpanService.Result result = rewrite ? LifeSpanService.set(living, ticks) : LifeSpanService.add(living, ticks);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.lifespan.failed", target.getDisplayName(),
                        Component.translatable("command.mxt.lifespan.failure."
                                + result.failure().name().toLowerCase(Locale.ROOT))));
                continue;
            }
            changed++;
            source.sendSuccess(() -> Component.translatable(
                    rewrite ? "command.mxt.lifespan.set" : "command.mxt.lifespan.add",
                    target.getDisplayName(), read(target)), true);
        }
        return changed;
    }

    // An account nobody opened is not the same answer as one that was spent, so the two read differently here.
    private static Component read(Entity target) {
        if (LifeSpanService.remaining(target) < 0L && LifeSpanService.total(target) < 0L)
            return Component.translatable("command.mxt.lifespan.unaccounted");
        return LifeSpanService.display(LifeSpanService.remaining(target), LifeSpanService.total(target),
                MxtServerConfig.INSTANCE.lifespan.ticksPerYear.getValue());
    }
}
