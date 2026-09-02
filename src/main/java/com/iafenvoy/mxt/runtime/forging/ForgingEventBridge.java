package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Returns every server-held workstation input if its owner dies before finishing.
 */
@EventBusSubscriber
public final class ForgingEventBridge {
    private ForgingEventBridge() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        ForgingWorkstationService.cancelAllOwnedOnDeath(player);
    }
}
