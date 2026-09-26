package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.context.condition.*;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public enum NeverCondition implements BiEntityCondition, BlockCondition, DamageCondition, EntityCondition, ItemCondition {
    INSTANCE;
    public static final MapCodec<NeverCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public @NotNull MapCodec<NeverCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(@NotNull ItemConditionContext context) {
        return false;
    }

    @Override
    public boolean test(@NotNull EntityConditionContext context) {
        return false;
    }

    @Override
    public boolean test(@NotNull DamageConditionContext context) {
        return false;
    }

    @Override
    public boolean test(@NotNull BlockConditionContext context) {
        return false;
    }

    @Override
    public boolean test(@NotNull BiEntityConditionContext context) {
        return false;
    }
}
