package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record HeightCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<HeightCondition> CODEC = Comparison.CODEC.xmap(HeightCondition::new, HeightCondition::comparison);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return this.comparison.compare(pos.getY());
    }

    @Override
    public MapCodec<HeightCondition> codec() {
        return CODEC;
    }
}
