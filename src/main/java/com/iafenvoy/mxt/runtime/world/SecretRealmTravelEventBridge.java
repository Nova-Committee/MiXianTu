package com.iafenvoy.mxt.runtime.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

/**
 * Returns travellers whose temporary secret realm was removed or expired while they were not loaded.
 */
@EventBusSubscriber
public final class SecretRealmTravelEventBridge {
    private SecretRealmTravelEventBridge() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        SecretRealmService.returnIfOrphaned(event.getEntity());
    }

    // Anything that cannot log in is checked when it joins a level instead, which also covers a server restart:
    // the travel attachment is loaded together with the entity.
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getEntity() instanceof ServerPlayer) return;
        if (event.getEntity() instanceof LivingEntity traveller) SecretRealmService.returnIfOrphaned(traveller);
    }
}
