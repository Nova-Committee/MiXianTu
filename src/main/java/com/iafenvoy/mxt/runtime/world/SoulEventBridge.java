package com.iafenvoy.mxt.runtime.world;

import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public final class SoulEventBridge {
    private SoulEventBridge() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) SoulService.transfer(event.getEntity(), "death");
    }
}
