package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService.RequestResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TradeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("trade")
                .requires(source -> source.getPlayer() != null)
                .then(argument("target", EntityArgument.player())
                        .executes(TradeCommand::trade)));
    }

    private static int trade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException(), target = EntityArgument.getPlayer(ctx, "target");
        RequestResult result = PlayerTradeService.request(player, target);
        switch (result) {
            case TOO_FAR -> player.sendSystemMessage(Component.translatable("command.mxt.trade.too_far"));
            case SELF -> player.sendSystemMessage(Component.translatable("command.mxt.trade.self"));
            case BUSY -> player.sendSystemMessage(Component.translatable("command.mxt.trade.busy"));
            case SENT -> {
                player.sendSystemMessage(Component.translatable("command.mxt.trade.request_sent"));
                target.sendSystemMessage(Component.translatable("command.mxt.trade.request", player.getDisplayName())
                        .withStyle(style -> style.withClickEvent(new RunCommand("/trade " + player.getGameProfile().name()))));
            }
        }
        return result == RequestResult.TOO_FAR || result == RequestResult.SELF || result == RequestResult.BUSY ? 0 : 1;
    }
}
