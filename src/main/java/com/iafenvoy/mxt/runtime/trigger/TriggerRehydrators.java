package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.ModuleHooks;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Registry of module-specific trigger rehydrators: each module's subscription index is runtime-only, and this is
 * what rebuilds it from the persisted attachment. A module whose rebuild throws is cleared rather than left with a
 * half-built index.
 */
public final class TriggerRehydrators {
    private TriggerRehydrators() {
    }

    public static void rehydrate(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        int before = TriggerDispatcher.subscriptionCount(entity.getUUID());
        for (TriggerRehydrator rehydrator : ModuleHooks.all(TriggerRehydrator.class)) {
            try {
                rehydrator.rehydrate(entity);
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Failed to rehydrate trigger module {} for entity {}",
                        rehydrator.module(), entity.getUUID(), exception);
                TriggerDispatcher.clearModule(entity.getUUID(), rehydrator.module());
            }
        }
        MiXianTu.LOGGER.debug("Rehydrated trigger subscriptions for {}: {} -> {}",
                entity.getUUID(), before, TriggerDispatcher.subscriptionCount(entity.getUUID()));
    }

    public static void clearEntity(UUID owner) {
        TriggerDispatcher.clearOwner(owner);
    }

    public static void clearAll() {
        TriggerDispatcher.clearAll();
    }
}
