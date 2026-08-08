package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record SequenceItemAction(List<ItemAction> actions) implements ItemAction {
    public static final MapCodec<SequenceItemAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceItemAction::new, SequenceItemAction::actions);

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        this.actions.forEach(action -> action.execute(holder, stack, context));
    }

    @Override
    public MapCodec<SequenceItemAction> codec() {
        return CODEC;
    }
}
