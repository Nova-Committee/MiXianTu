package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;

/**
 * Passes when at least one nested damage condition passes.
 */
public record OrDamageCondition(List<DamageCondition> conditions) implements DamageCondition {
    public static final MapCodec<OrDamageCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrDamageCondition::new, OrDamageCondition::conditions);

    @Override
    public boolean test(DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return this.conditions.stream().anyMatch(condition -> condition.test(source, amount, ctx));
    }

    @Override
    public MapCodec<OrDamageCondition> codec() {
        return CODEC;
    }
}
