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
        // Suspended: death no longer manifests a soul. The attachment is not written either, so a death leaves
        // nothing behind to reclaim and /mxt soul reclaim has nothing to find. Uncomment to bring it back.
        // if (!event.getEntity().level().isClientSide()) SoulService.transfer(event.getEntity(), "death");
    }
}
