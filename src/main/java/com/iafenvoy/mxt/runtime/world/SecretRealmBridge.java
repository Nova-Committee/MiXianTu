package com.iafenvoy.mxt.runtime.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Secret realm lifecycle hooks. Expiry runs on the overworld's clock rather than on each dimension's own
 * tick, because a secret realm whose players have all left is not being ticked at all.
 */
@EventBusSubscriber
public final class SecretRealmBridge {
    private SecretRealmBridge() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SecretRealmRegistry.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SecretRealmRegistry.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (level.getGameTime() % 20L != 0L) return;
        for (SecretRealmRecord record : SecretRealmRegistry.all())
            SecretRealmService.expire(level.getServer(), record, level.getGameTime());
    }
}
