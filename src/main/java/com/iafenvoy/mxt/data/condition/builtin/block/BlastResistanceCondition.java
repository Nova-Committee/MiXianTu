package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record BlastResistanceCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<BlastResistanceCondition> CODEC = Comparison.CODEC.xmap(BlastResistanceCondition::new, BlastResistanceCondition::comparison);

    @Override
    public boolean test(BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        return this.comparison.compare(level.getBlockState(pos).getBlock().getExplosionResistance());
    }

    @Override
    public MapCodec<BlastResistanceCondition> codec() {
        return CODEC;
    }
}
