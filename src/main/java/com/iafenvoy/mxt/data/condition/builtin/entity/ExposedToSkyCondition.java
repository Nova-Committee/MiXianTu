package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public enum ExposedToSkyCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<ExposedToSkyCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return entity.level().canSeeSky(entity.blockPosition());
    }

    @Override
    public @NonNull MapCodec<ExposedToSkyCondition> codec() {
        return CODEC;
    }
}
