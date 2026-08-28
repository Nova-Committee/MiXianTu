package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public enum AlwaysTrueBlockCondition implements BlockCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueBlockCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueBlockCondition> codec() {
        return CODEC;
    }
}
