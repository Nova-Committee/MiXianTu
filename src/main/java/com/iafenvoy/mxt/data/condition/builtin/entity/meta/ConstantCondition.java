package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record ConstantCondition(boolean value) implements EntityCondition {
    public static final MapCodec<ConstantCondition> CODEC = Codec.BOOL.fieldOf("value").xmap(ConstantCondition::new, ConstantCondition::value);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.value;
    }

    @Override
    public MapCodec<ConstantCondition> codec() {
        return CODEC;
    }
}
