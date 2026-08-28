package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record AndBiEntityCondition(List<BiEntityCondition> conditions) implements BiEntityCondition {
    public static final MapCodec<AndBiEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBiEntityCondition::new, AndBiEntityCondition::conditions);

    @Override
    public boolean test(BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return this.conditions.stream().allMatch(condition -> condition.test(actor, target, ctx));
    }

    @Override
    public MapCodec<AndBiEntityCondition> codec() {
        return CODEC;
    }
}
