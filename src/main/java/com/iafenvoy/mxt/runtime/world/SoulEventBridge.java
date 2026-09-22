package com.iafenvoy.mxt.runtime.world;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber
public final class SoulEventBridge {
    private SoulEventBridge() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        // Suspended: death no longer manifests a soul, and the attachment is not written either, so a death
        // leaves nothing behind to reclaim. The call below is the switch that brings it back.
        // if (!event.getEntity().level().isClientSide()) SoulService.transfer(event.getEntity(), "death");
    }
}
