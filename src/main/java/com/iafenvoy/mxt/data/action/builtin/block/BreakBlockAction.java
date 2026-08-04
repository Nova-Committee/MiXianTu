package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record BreakBlockAction(boolean drop) implements BlockAction {
    public static final MapCodec<BreakBlockAction> CODEC = Codec.BOOL.optionalFieldOf("drop", true).xmap(BreakBlockAction::new, BreakBlockAction::drop);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        level.destroyBlock(pos, this.drop);
    }

    @Override
    public MapCodec<BreakBlockAction> codec() {
        return CODEC;
    }
}
