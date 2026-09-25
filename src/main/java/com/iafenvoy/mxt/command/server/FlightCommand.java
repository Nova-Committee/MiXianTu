package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt flight} subtree: operator tools for the flight the source player is on right now. Also a top-level
 * {@code /flight} where the server option allows it.
 */
public final class FlightCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("flight")
                .requires(ServerCommandManager::mayChange)
                .then(literal("fill").executes(ctx -> fill(ctx.getSource())));
    }

    // A seat offset is a number nobody can judge from the JSON, so this fills every seat still open with a body and
    // lets the operator look at where they landed. The markers leave with the flight (see the mount's removal path).
    private static int fill(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        if (!(player.getVehicle() instanceof FlyingSwordEntity sword)) {
            source.sendFailure(Component.translatable("command.mxt.flight.not_flying"));
            return 0;
        }
        if (sword.freeSeats() <= 0) {
            source.sendFailure(Component.translatable("command.mxt.flight.full", sword.seats()));
            return 0;
        }
        int filled = FlightService.fillSeats(sword);
        source.sendSuccess(() -> Component.translatable("command.mxt.flight.filled", filled, sword.seats()), true);
        return filled;
    }
}
