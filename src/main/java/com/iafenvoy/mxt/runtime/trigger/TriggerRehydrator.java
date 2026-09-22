package com.iafenvoy.mxt.runtime.trigger;

import net.minecraft.world.entity.LivingEntity;

/**
 * Rebuilds one module's runtime trigger subscriptions from its persisted gameplay state. Implementations must be
 * idempotent - rehydration runs again for every loaded entity - and never persist subscription objects.
 */
public interface TriggerRehydrator {
    // Stable module name, used for diagnostics and subscription cleanup.
    String module();

    void rehydrate(LivingEntity entity);
}
