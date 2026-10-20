package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.event.dispatcher.TriggerRehydrateEvent;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * Drives the trigger rehydration of every module for one loaded body. Each module's subscription index is runtime-only
 * and rebuilt from its own attachment, and {@link TriggerRehydrateEvent#rehydrate} is what isolates a module that throws.
 */
public final class TriggerRehydrators {
    // One line per loaded body, which on a populated server means hundreds per load.
    private static final LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.DEBUG, LoggingLevel.NONE);

    private TriggerRehydrators() {
    }

    public static void rehydrate(LivingEntity entity) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            if (entity.level().isClientSide()) return;
            int before = TriggerDispatcher.subscriptionCount(entity.getUUID());
            NeoForge.EVENT_BUS.post(new TriggerRehydrateEvent(entity));
            LOGGER.log("Rehydrated trigger subscriptions for {}: {} -> {}",
                    entity.getUUID(), before, TriggerDispatcher.subscriptionCount(entity.getUUID()));
        }
    }

    public static void clearEntity(UUID owner) {
        TriggerDispatcher.clearOwner(owner);
    }

    public static void clearAll() {
        TriggerDispatcher.clearAll();
    }
}
