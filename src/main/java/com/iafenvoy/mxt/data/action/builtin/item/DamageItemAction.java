package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record DamageItemAction(NumberProvider amount) implements ItemAction {
    public static final MapCodec<DamageItemAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageItemAction::new, DamageItemAction::amount);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        ItemStack stack = ctx.stack();
        if (!stack.isDamageableItem()) return;
        double amount = this.amount.evaluate(ctx.formula());
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + Math.max(1, (int) Math.round(amount))));
    }

    @Override
    public @NonNull MapCodec<DamageItemAction> codec() {
        return CODEC;
    }
}
