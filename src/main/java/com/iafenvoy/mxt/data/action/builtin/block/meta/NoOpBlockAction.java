package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public enum NoOpBlockAction implements BlockAction {
    INSTANCE;
    public static final MapCodec<NoOpBlockAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
    }

    @Override
    public MapCodec<NoOpBlockAction> codec() {
        return CODEC;
    }
}
