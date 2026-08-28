package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum GlowingCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<GlowingCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity.isCurrentlyGlowing();
    }

    @Override
    public MapCodec<GlowingCondition> codec() {
        return CODEC;
    }
}
