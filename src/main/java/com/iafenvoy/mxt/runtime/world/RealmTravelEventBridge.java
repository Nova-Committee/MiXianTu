package com.iafenvoy.mxt.runtime.world;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Returns players whose temporary realm was removed or expired while they were offline.
 */
@EventBusSubscriber
public final class RealmTravelEventBridge {
    private RealmTravelEventBridge() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RealmInstanceService.returnIfOrphaned(player);
    }
}
