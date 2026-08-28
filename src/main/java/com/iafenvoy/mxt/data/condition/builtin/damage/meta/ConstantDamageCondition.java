package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;

/**
 * A constant damage-condition result.
 */
public record ConstantDamageCondition(boolean value) implements DamageCondition {
    public static final MapCodec<ConstantDamageCondition> CODEC = Codec.BOOL.fieldOf("value").xmap(ConstantDamageCondition::new, ConstantDamageCondition::value);

    @Override
    public boolean test(DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return this.value;
    }

    @Override
    public MapCodec<ConstantDamageCondition> codec() {
        return CODEC;
    }
}
