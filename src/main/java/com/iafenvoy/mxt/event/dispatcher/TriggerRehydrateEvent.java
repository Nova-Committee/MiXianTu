package com.iafenvoy.mxt.event.dispatcher;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.function.Consumer;

/**
 * Rebuilds the runtime trigger subscriptions of one loaded body, one module's answer at a time. Every module asks for
 * its own rebuild through {@link #rehydrate}, which is also where a throwing module is cleared instead of being left
 * with a half-built index.
 */
public final class TriggerRehydrateEvent extends LivingEvent {
    public TriggerRehydrateEvent(LivingEntity entity) {
        super(entity);
    }

    // Also clears this module's subscriptions on failure: a half-rebuilt index answers signals the body no longer has.
    public void rehydrate(String module, Consumer<LivingEntity> rebuild) {
        LivingEntity entity = this.getEntity();
        try {
            rebuild.accept(entity);
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Failed to rehydrate trigger module {} for entity {}", module, entity.getUUID(), exception);
            TriggerDispatcher.clearModule(entity.getUUID(), module);
        }
    }
}
