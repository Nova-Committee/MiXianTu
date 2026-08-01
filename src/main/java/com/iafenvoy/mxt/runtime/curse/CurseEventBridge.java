package com.iafenvoy.mxt.runtime.curse;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Registers loaded cursed entities and services only their due lifecycle work.
 */
public final class CurseEventBridge {
    private CurseEventBridge() {
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) CurseScheduler.reschedule(event.getEntity());
    }

    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level) CurseScheduler.onLevelTick(level);
    }
}
