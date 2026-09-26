package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record AirCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<AirCondition> CODEC = Comparison.CODEC.xmap(AirCondition::new, AirCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return this.comparison.compare(ctx.entity().getAirSupply());
    }

    @Override
    public @NonNull MapCodec<AirCondition> codec() {
        return CODEC;
    }
}
