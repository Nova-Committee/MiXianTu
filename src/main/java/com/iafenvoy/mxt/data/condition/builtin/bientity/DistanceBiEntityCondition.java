package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record DistanceBiEntityCondition(NumberProvider maximum) implements BiEntityCondition {
    public static final MapCodec<DistanceBiEntityCondition> CODEC = NumberProvider.CODEC.fieldOf("maximum").xmap(DistanceBiEntityCondition::new, DistanceBiEntityCondition::maximum);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        double maximum = this.maximum.evaluate(ctx.formula());
        return Double.isFinite(maximum) && maximum >= 0.0D && ctx.actor().distanceToSqr(ctx.target()) <= maximum * maximum;
    }

    @Override
    public @NonNull MapCodec<DistanceBiEntityCondition> codec() {
        return CODEC;
    }
}
