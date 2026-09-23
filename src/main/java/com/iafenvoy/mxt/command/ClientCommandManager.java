package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.command.client.HudCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.function.Function;

/**
 * The client half of the command surface: nodes that exist only in the player's own game. They take no alias
 * behind a server option, because a server-side switch cannot reach a client that is not running one.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ClientCommandManager {
    private static final List<Function<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>>> NODES =
            List.of(_ -> HudCommand.build());

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandBuildContext context = event.getBuildContext();
        for (Function<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>> node : NODES)
            event.getDispatcher().register(node.apply(context));
    }
}
