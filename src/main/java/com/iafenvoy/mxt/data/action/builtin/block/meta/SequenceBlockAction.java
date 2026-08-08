package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record SequenceBlockAction(List<BlockAction> actions) implements BlockAction {
    public static final MapCodec<SequenceBlockAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBlockAction::new, SequenceBlockAction::actions);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        this.actions.forEach(action -> action.execute(level, pos, context));
    }

    @Override
    public MapCodec<SequenceBlockAction> codec() {
        return CODEC;
    }
}
