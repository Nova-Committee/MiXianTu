package com.iafenvoy.mxt.command.client;

import com.iafenvoy.mxt.screen.wheel.content.WheelConfigurationScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/**
 * The client command that opens the wheel editor, also reachable as {@code /wheel configure}. It never reaches the
 * server: the editor reads the synced attachments and registries, so opening it needs no round trip and no permission.
 */
public final class WheelCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("wheel").executes(WheelCommand::open);
    }

    // open() answers false without a player, so the message below replaces what would otherwise be a null-player crash.
    private static int open(CommandContext<CommandSourceStack> context) {
        if (WheelConfigurationScreen.open()) return 1;
        context.getSource().sendFailure(Component.translatable("command.mxt.wheel.unavailable"));
        return 0;
    }
}
