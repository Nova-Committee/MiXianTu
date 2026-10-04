package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.runtime.EntityTickStages;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import net.minecraft.world.entity.LivingEntity;

/**
 * The slow progression pass: mastery is measured by a stored value, so it is re-read on a 20-tick cadence, and
 * whatever advanced decides for itself what a level grants - that rebuild is one call for every granting system.
 */
public final class ProgressionTickStage {
    private static final long INTERVAL_TICKS = 20L;

    private ProgressionTickStage() {
    }

    public static void register() {
        EntityTickStages.register("progression", EntityTickStages.PROGRESSION, ProgressionTickStage::tick);
    }

    private static void tick(LivingEntity entity) {
        if (entity.level().getGameTime() % INTERVAL_TICKS != 0L) return;
        if (ProgressionDriver.tick(entity)) AbilityGrantService.recalculate(entity);
    }
}
