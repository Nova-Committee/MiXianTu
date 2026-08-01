package com.iafenvoy.mxt.runtime.world;

import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class SoulEventBridge {
    private SoulEventBridge() {
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) SoulService.transfer(event.getEntity(), "death");
    }
}
