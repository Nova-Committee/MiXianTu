package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public enum AlwaysTrueItemCondition implements ItemCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueItemCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueItemCondition> codec() {
        return CODEC;
    }
}
