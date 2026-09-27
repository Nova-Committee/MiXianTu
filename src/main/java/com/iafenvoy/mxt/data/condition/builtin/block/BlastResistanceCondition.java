package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Compares the block's blast resistance. Reads the base value on purpose: the NeoForge replacement for
 * {@code getExplosionResistance()} needs an explosion, which this condition has none of.
 */
@SuppressWarnings("deprecation")
public record BlastResistanceCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<BlastResistanceCondition> CODEC = Comparison.CODEC.xmap(BlastResistanceCondition::new, BlastResistanceCondition::comparison);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return this.comparison.compare(ctx.level().getBlockState(ctx.pos()).getBlock().getExplosionResistance());
    }

    @Override
    public @NonNull MapCodec<BlastResistanceCondition> codec() {
        return CODEC;
    }
}
