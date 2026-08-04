package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum GlowingCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<GlowingCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity entity) {
        return entity.isCurrentlyGlowing();
    }

    @Override
    public MapCodec<GlowingCondition> codec() {
        return CODEC;
    }
}
