package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public enum SneakingEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<SneakingEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().isShiftKeyDown();
    }

    @Override
    public @NonNull MapCodec<SneakingEntityCondition> codec() {
        return CODEC;
    }
}
