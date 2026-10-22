package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class TravelDiagnostics {
    private static final LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.WARN, LoggingLevel.WARN);

    private TravelDiagnostics() {
    }

    public static void rejected(Entity entity, ServerLevel target, Vec3 position, String stage, String reason, Object detail) {
        LOGGER.log("Travel rejected: stage={} reason={} entity={} type={} source={} from={} target={} to={} bounds={} detail={}",
                stage, reason, entity.getUUID(), entity.getType(), entity.level().dimension().identifier(), entity.position(),
                target.dimension().identifier(), position, entity.getBoundingBox(), detail);
    }
}
