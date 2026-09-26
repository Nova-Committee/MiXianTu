package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public enum GlowingCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<GlowingCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().isCurrentlyGlowing();
    }

    @Override
    public @NonNull MapCodec<GlowingCondition> codec() {
        return CODEC;
    }
}
