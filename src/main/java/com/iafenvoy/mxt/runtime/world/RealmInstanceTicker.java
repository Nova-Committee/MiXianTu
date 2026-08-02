package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

public final class RealmInstanceTicker {
    private RealmInstanceTicker() {
    }

    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20L == 0L)
            RealmInstanceService.expire(level, level.getData(MxtAttachments.REALM_INSTANCE), level.getGameTime());
    }
}
