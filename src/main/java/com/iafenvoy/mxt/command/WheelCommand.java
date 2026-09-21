package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.screen.wheel.content.WheelConfigurationScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import static net.minecraft.commands.Commands.literal;

/**
 * Client command that opens the wheel editor; it only reads synced data, so the server has nothing to do.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("wheel").executes(context -> {
        if (WheelConfigurationScreen.open()) return 1;
        context.getSource().sendFailure(Component.translatable("command.mxt.wheel.unavailable"));
        return 0;
    });

    private WheelCommand() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(ROOT);
    }
}
