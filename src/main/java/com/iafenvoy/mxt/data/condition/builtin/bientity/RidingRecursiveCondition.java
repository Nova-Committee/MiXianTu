package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public enum RidingRecursiveCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<RidingRecursiveCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        for (Entity vehicle = ctx.actor().getVehicle(); vehicle != null; vehicle = vehicle.getVehicle())
            if (vehicle == ctx.target()) return true;
        return false;
    }

    @Override
    public @NonNull MapCodec<RidingRecursiveCondition> codec() {
        return CODEC;
    }
}
