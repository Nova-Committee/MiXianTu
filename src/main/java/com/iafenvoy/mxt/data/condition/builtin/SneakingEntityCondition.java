package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum SneakingEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<SneakingEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity entity) {
        return entity.isShiftKeyDown();
    }

    @Override
    public MapCodec<SneakingEntityCondition> codec() {
        return CODEC;
    }
}
