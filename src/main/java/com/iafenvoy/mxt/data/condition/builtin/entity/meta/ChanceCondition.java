package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record ChanceCondition(double chance) implements EntityCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity.getRandom().nextDouble() < this.chance;
    }

    @Override
    public MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
