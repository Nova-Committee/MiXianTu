package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.WeightedActionEntry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ChoiceAction(List<WeightedActionEntry<ItemAction>> actions) implements ItemAction {
    public static final MapCodec<ChoiceAction> CODEC = WeightedActionEntry.codec(ItemAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    public ChoiceAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        WeightedActionEntry<ItemAction> entry = WeightedActionEntry.select(this.actions, holder.getRandom());
        if (entry != null) entry.element().execute(holder, stack, context);
    }

    @Override
    public MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
