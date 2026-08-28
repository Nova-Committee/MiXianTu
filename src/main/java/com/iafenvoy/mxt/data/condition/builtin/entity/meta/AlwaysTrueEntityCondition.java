package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum AlwaysTrueEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueEntityCondition> codec() {
        return CODEC;
    }
}
