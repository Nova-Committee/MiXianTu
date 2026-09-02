package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.WeightedActionEntry;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ChoiceAction(List<WeightedActionEntry<ItemAction>> actions) implements ItemAction {
    public static final MapCodec<ChoiceAction> CODEC = WeightedActionEntry.codec(ItemAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        WeightedActionEntry<ItemAction> entry = WeightedActionEntry.select(this.actions, holder.getRandom());
        if (entry != null) entry.element().execute(holder, stack, ctx);
    }

    @Override
    public @NonNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
