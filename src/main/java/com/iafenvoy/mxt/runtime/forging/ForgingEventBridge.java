package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

/**
 * Lifecycle hooks for the forge table: strike rate-limit bookkeeping.
 *
 * <p>Session state lives on the block entity, so there is no world-level registry to prune and no
 * cross-dimension input recovery to perform. The only cross-cutting state is the rate limiter,
 * which must not leak entries for players or tables that no longer exist.</p>
 */
@EventBusSubscriber
public final class ForgingEventBridge {
    private static final int PRUNE_INTERVAL_TICKS = 600;

    private ForgingEventBridge() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        ForgingRateLimiter.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        int ticks = event.getServer().getTickCount();
        if (ticks % PRUNE_INTERVAL_TICKS != 0) return;
        ForgingRateLimiter.prune(event.getServer(), ticks);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        ForgingRateLimiter.forget(player.getUUID());
    }
}
