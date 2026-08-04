package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum ExposedToSkyCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<ExposedToSkyCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity entity) {
        return entity.level().canSeeSky(entity.blockPosition());
    }

    @Override
    public MapCodec<ExposedToSkyCondition> codec() {
        return CODEC;
    }
}
