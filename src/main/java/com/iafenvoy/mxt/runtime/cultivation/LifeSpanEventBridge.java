package com.iafenvoy.mxt.runtime.cultivation;

import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

public final class LifeSpanEventBridge {
    private LifeSpanEventBridge() {
    }

    public static void onEntityTick(Post event) {
        if (!event.getEntity().level().isClientSide()) LifeSpanService.tick(event.getEntity());
    }
}
