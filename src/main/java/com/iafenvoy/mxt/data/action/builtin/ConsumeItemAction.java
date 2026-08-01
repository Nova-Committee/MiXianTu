package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record ConsumeItemAction(NumberProvider count) implements ItemAction {
    public static final MapCodec<ConsumeItemAction> CODEC = NumberProvider.CODEC.fieldOf("count").xmap(ConsumeItemAction::new, ConsumeItemAction::count);

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        double count = this.count.evaluate(context);
        if (Double.isFinite(count) && count > 0.0D) stack.shrink(Math.max(1, (int) Math.round(count)));
    }

    @Override
    public MapCodec<ConsumeItemAction> codec() {
        return CODEC;
    }
}
