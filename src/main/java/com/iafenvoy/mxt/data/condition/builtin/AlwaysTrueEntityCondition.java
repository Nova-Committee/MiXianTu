package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum AlwaysTrueEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity entity) {
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueEntityCondition> codec() {
        return CODEC;
    }
}
