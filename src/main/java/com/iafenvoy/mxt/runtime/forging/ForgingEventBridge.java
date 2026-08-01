package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Returns every server-held workstation input if its owner dies before finishing.
 */
public final class ForgingEventBridge {
    private ForgingEventBridge() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        ForgingWorkstationService.cancelAllOwnedOnDeath(player);
    }
}
