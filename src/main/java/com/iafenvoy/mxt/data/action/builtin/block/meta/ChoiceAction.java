package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.WeightedActionEntry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record ChoiceAction(List<WeightedActionEntry<BlockAction>> actions) implements BlockAction {
    public static final MapCodec<ChoiceAction> CODEC = WeightedActionEntry.codec(BlockAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    public ChoiceAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        WeightedActionEntry<BlockAction> entry = WeightedActionEntry.select(this.actions, level.getRandom());
        if (entry != null) entry.element().execute(level, pos, context);
    }

    @Override
    public MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
