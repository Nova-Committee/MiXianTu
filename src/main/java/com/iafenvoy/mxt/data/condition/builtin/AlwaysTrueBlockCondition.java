package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public enum AlwaysTrueBlockCondition implements BlockCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueBlockCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueBlockCondition> codec() {
        return CODEC;
    }
}
