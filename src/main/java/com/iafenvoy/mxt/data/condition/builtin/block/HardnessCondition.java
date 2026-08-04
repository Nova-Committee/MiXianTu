package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record HardnessCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<HardnessCondition> CODEC = Comparison.CODEC.xmap(HardnessCondition::new, HardnessCondition::comparison);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return this.comparison.compare(level.getBlockState(pos).getDestroySpeed(level, pos));
    }

    @Override
    public MapCodec<HardnessCondition> codec() {
        return CODEC;
    }
}
