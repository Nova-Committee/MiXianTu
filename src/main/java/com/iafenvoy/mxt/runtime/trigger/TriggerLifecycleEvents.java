package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.ServerCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Owns the common lifecycle for runtime-only trigger subscriptions.
 */
@EventBusSubscriber
public final class TriggerLifecycleEvents {
    private TriggerLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rehydrateServer(event.getServer(), "server start");
    }

    @SubscribeEvent
    public static void onDatapackReload(ServerDataLoad event) {
        TriggerRehydrators.clearAll();
        ServerCache.get().ifPresent(cache -> rehydrateServer(cache.server(), "datapack reload"));
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity)
            TriggerRehydrators.rehydrate(entity);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        TriggerRehydrators.clearEntity(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TriggerRehydrators.clearAll();
    }

    private static void rehydrateServer(MinecraftServer server, String reason) {
        TriggerRehydrators.clearAll();
        int[] entities = {0};
        server.getAllLevels().forEach(level -> level.getEntities().getAll().forEach(entity -> {
            if (entity instanceof LivingEntity living) {
                entities[0]++;
                TriggerRehydrators.rehydrate(living);
            }
        }));
        MiXianTu.LOGGER.info("Rehydrated trigger data after {}: {} living entities, {} active subscriptions {}",
                reason, entities[0], TriggerDispatcher.subscriptionCount(), TriggerDispatcher.subscriptionCountsByModule());
    }
}
