package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/** A reusable false default for optional stop conditions. */
public enum NeverEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<NeverEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity entity) {
        return false;
    }

    @Override
    public MapCodec<NeverEntityCondition> codec() {
        return CODEC;
    }
}
