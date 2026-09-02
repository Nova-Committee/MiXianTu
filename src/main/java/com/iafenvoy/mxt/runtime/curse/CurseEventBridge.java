package com.iafenvoy.mxt.runtime.curse;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Registers loaded cursed entities and services only their due lifecycle work.
 */
@EventBusSubscriber
public final class CurseEventBridge {
    private CurseEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) CurseScheduler.reschedule(event.getEntity());
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level) CurseScheduler.onLevelTick(level);
    }
}
