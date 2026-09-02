package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of module-specific trigger rehydrators.
 */
public final class TriggerRehydrators {
    private static final Map<String, TriggerRehydrator> REHYDRATORS = new LinkedHashMap<>();

    private TriggerRehydrators() {
    }

    public static synchronized void register(TriggerRehydrator rehydrator) {
        TriggerRehydrator previous = REHYDRATORS.put(rehydrator.module(), rehydrator);
        if (previous != null && previous != rehydrator)
            MiXianTu.LOGGER.warn("Replacing trigger rehydrator for module {}", rehydrator.module());
    }

    public static void rehydrate(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        int before = TriggerDispatcher.subscriptionCount(entity.getUUID());
        for (TriggerRehydrator rehydrator : snapshot()) {
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

    private static TriggerRehydrator[] snapshot() {
        synchronized (TriggerRehydrators.class) {
            return REHYDRATORS.values().toArray(TriggerRehydrator[]::new);
        }
    }
}
