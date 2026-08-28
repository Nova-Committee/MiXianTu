package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record NotEntityCondition(EntityCondition condition) implements EntityCondition {
    public static final MapCodec<NotEntityCondition> CODEC = SINGLE_CODEC.fieldOf("condition").xmap(NotEntityCondition::new, NotEntityCondition::condition);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return !this.condition.test(entity, ctx);
    }

    @Override
    public MapCodec<NotEntityCondition> codec() {
        return CODEC;
    }
}
