package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.saturn.event.CollectTickStagesEvent;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.runtime.EntityTickStages;
import com.iafenvoy.saturn.runtime.ability.AbilityGrantService;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The slow progression pass: mastery is measured by a stored value, so it is re-read on a 20-tick cadence, and
 * whatever advanced decides for itself what a level grants - that rebuild is one call for every granting system.
 */
@EventBusSubscriber
public final class ProgressionTickStage {
    private static final long INTERVAL_TICKS = 20L;

    private ProgressionTickStage() {
    }

    @SubscribeEvent
    public static void onCollectStages(CollectTickStagesEvent event) {
        event.add("progression", EntityTickStages.PROGRESSION, ProgressionTickStage::tick);
    }

    private static void tick(LivingEntity entity) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.PROGRESSION)) {
            if (entity.level().getGameTime() % INTERVAL_TICKS != 0L) return;
            if (ProgressionDriver.tick(entity)) AbilityGrantService.recalculate(entity);
        }
    }
}
