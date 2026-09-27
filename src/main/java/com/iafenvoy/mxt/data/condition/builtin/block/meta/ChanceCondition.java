package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record ChanceCondition(double chance) implements BlockCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return ctx.level().getRandom().nextDouble() < this.chance;
    }

    @Override
    public @NonNull MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
