package com.iafenvoy.mxt.runtime.trigger;

import net.minecraft.world.entity.LivingEntity;

/**
 * Rebuilds one module's runtime trigger subscriptions from persisted gameplay
 * state. Implementations must be idempotent and must not persist subscription
 * objects themselves.
 */
public interface TriggerRehydrator {
    /**
     * Stable module name used for diagnostics and subscription cleanup.
     */
    String module();

    /**
     * Rebuilds this module's subscriptions for the supplied loaded entity.
     */
    void rehydrate(LivingEntity entity);
}
