package com.iafenvoy.mxt.runtime.world;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

/**
 * Returns players whose temporary secret realm was removed or expired while they were offline.
 */
@EventBusSubscriber
public final class SecretRealmTravelEventBridge {
    private SecretRealmTravelEventBridge() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SecretRealmService.returnIfOrphaned(player);
    }
}
