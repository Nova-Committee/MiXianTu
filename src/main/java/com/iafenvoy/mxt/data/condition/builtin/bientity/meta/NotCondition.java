package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record NotCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<NotCondition> CODEC = BiEntityCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return !this.condition.test(actor, target, ctx);
    }

    @Override
    public MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
