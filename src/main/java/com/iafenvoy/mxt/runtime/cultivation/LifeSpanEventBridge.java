package com.iafenvoy.mxt.runtime.cultivation;

import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public final class LifeSpanEventBridge {
    private LifeSpanEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (!event.getEntity().level().isClientSide()) LifeSpanService.tick(event.getEntity());
    }
}
