package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.context.condition.*;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public enum AlwaysTrueCondition implements BiEntityCondition, BlockCondition, DamageCondition, EntityCondition, ItemCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public @NotNull MapCodec<AlwaysTrueCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(@NotNull ItemConditionContext context) {
        return true;
    }

    @Override
    public boolean test(@NotNull EntityConditionContext context) {
        return true;
    }

    @Override
    public boolean test(@NotNull DamageConditionContext context) {
        return true;
    }

    @Override
    public boolean test(@NotNull BlockConditionContext context) {
        return true;
    }

    @Override
    public boolean test(@NotNull BiEntityConditionContext context) {
        return true;
    }
}
