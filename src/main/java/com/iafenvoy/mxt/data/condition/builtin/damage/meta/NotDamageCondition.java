package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Negates a nested damage condition.
 */
public record NotDamageCondition(DamageCondition condition) implements DamageCondition {
    public static final MapCodec<NotDamageCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DamageCondition.CODEC.fieldOf("condition").forGetter(NotDamageCondition::condition)
    ).apply(i, NotDamageCondition::new));

    @Override
    public boolean test(DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return !this.condition.test(source, amount, ctx);
    }

    @Override
    public MapCodec<NotDamageCondition> codec() {
        return CODEC;
    }
}
