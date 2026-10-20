package com.iafenvoy.mxt.event.dispatcher;

import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.function.Consumer;

/**
 * Rebuilds the runtime trigger subscriptions of one loaded body, one module's answer at a time. Every module asks for
 * its own rebuild through {@link #rehydrate}, which is also where a throwing module is cleared instead of being left
 * with a half-built index.
 */
public final class TriggerRehydrateEvent extends LivingEvent {
    private static final LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.ERROR, LoggingLevel.ERROR);

    public TriggerRehydrateEvent(LivingEntity entity) {
        super(entity);
    }

    // Also clears this module's subscriptions on failure: a half-rebuilt index answers signals the body no longer has.
    public void rehydrate(String module, Consumer<LivingEntity> rebuild) {
        LivingEntity entity = this.getEntity();
        try {
            rebuild.accept(entity);
        } catch (RuntimeException exception) {
            LOGGER.log("Failed to rehydrate trigger module '" + module + "' for entity " + entity.getUUID(), exception);
            TriggerDispatcher.clearModule(entity.getUUID(), module);
        }
    }
}
