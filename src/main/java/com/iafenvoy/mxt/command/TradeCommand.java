package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService.RequestResult;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TradeCommand {
    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("trade")
                .requires(source -> source.getPlayer() != null)
                .then(argument("target", EntityArgument.player())
                        .executes(ctx -> trade(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
    }

    private static int trade(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        RequestResult result = PlayerTradeService.request(player, target);
        switch (result) {
            case TOO_FAR -> player.sendSystemMessage(Component.translatable("mxt.command.trade.too_far"));
            case SELF -> player.sendSystemMessage(Component.translatable("mxt.command.trade.self"));
            case BUSY -> player.sendSystemMessage(Component.translatable("mxt.command.trade.busy"));
            case SENT -> {
                player.sendSystemMessage(Component.translatable("mxt.command.trade.request_sent"));
                target.sendSystemMessage(Component.translatable("mxt.command.trade.request", player.getDisplayName())
                        .withStyle(style -> style.withClickEvent(new RunCommand("/trade " + player.getGameProfile().name()))));
            }
            case STARTED -> {
            }
        }
        return result == RequestResult.TOO_FAR || result == RequestResult.SELF || result == RequestResult.BUSY ? 0 : 1;
    }
}
