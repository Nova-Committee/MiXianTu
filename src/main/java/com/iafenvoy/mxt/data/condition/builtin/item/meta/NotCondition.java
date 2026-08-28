package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record NotCondition(ItemCondition condition) implements ItemCondition {
    public static final MapCodec<NotCondition> CODEC = ItemCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return !this.condition.test(holder, stack, ctx);
    }

    @Override
    public MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
