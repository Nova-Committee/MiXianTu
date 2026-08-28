package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public enum UsingItemCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<UsingItemCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof LivingEntity living && living.isUsingItem();
    }

    @Override
    public MapCodec<UsingItemCondition> codec() {
        return CODEC;
    }
}
