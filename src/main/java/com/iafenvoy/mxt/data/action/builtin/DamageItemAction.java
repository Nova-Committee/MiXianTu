package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record DamageItemAction(NumberProvider amount) implements ItemAction {
    public static final MapCodec<DamageItemAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageItemAction::new, DamageItemAction::amount);

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        if (!stack.isDamageableItem()) return;
        double amount = this.amount.evaluate(context);
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + Math.max(1, (int) Math.round(amount))));
    }

    @Override
    public MapCodec<DamageItemAction> codec() {
        return CODEC;
    }
}
