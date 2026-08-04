package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record ConstantCondition(boolean value) implements BlockCondition {
    public static final MapCodec<ConstantCondition> CODEC = Codec.BOOL.fieldOf("value").xmap(ConstantCondition::new, ConstantCondition::value);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return this.value;
    }

    @Override
    public MapCodec<ConstantCondition> codec() {
        return CODEC;
    }
}
