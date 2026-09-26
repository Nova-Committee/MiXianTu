package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record SlipperinessCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<SlipperinessCondition> CODEC = Comparison.CODEC.xmap(SlipperinessCondition::new, SlipperinessCondition::comparison);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return this.comparison.compare(ctx.level().getBlockState(ctx.pos()).getBlock().getFriction());
    }

    @Override
    public @NonNull MapCodec<SlipperinessCondition> codec() {
        return CODEC;
    }
}
