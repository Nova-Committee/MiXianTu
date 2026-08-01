package com.iafenvoy.mxt.runtime.world;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

/**
 * Returns players whose temporary realm was removed or expired while they were offline.
 */
public final class RealmTravelEventBridge {
    private RealmTravelEventBridge() {
    }

    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RealmInstanceService.returnIfOrphaned(player);
    }
}
