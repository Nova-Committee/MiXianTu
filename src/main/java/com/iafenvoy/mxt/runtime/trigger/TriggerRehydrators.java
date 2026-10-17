package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.event.dispatcher.TriggerRehydrateEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * Drives the trigger rehydration of every module for one loaded body. Each module's subscription index is runtime-only
 * and rebuilt from its own attachment, and {@link TriggerRehydrateEvent#rehydrate} is what isolates a module that throws.
 */
public final class TriggerRehydrators {
    private TriggerRehydrators() {
    }

    public static void rehydrate(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        int before = TriggerDispatcher.subscriptionCount(entity.getUUID());
        NeoForge.EVENT_BUS.post(new TriggerRehydrateEvent(entity));
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
