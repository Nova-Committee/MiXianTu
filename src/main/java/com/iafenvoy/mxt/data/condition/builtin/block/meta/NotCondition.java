package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record NotCondition(BlockCondition condition) implements BlockCondition {
    public static final MapCodec<NotCondition> CODEC = BlockCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return !this.condition.test(level, pos, context);
    }

    @Override
    public MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
