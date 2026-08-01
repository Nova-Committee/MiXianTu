package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record SequenceBlockAction(List<BlockAction> actions) implements BlockAction {
    public static final MapCodec<SequenceBlockAction> CODEC = BlockAction.SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBlockAction::new, SequenceBlockAction::actions);

    public SequenceBlockAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        this.actions.forEach(action -> action.execute(level, pos, context));
    }

    @Override
    public MapCodec<SequenceBlockAction> codec() {
        return CODEC;
    }
}
