package com.iafenvoy.mxt.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-safe status feedback for framework item interactions.
 */
final class ItemFeedback {
    private ItemFeedback() {
    }

    static void send(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
    }
}
