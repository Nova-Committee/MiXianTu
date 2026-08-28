package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Matches vanilla damage sources classified as magic by the same tags Origins uses.
 */
public final class MagicDamageCondition implements DamageCondition {
    public static final MagicDamageCondition INSTANCE = new MagicDamageCondition();
    public static final MapCodec<MagicDamageCondition> CODEC = MapCodec.unit(() -> INSTANCE);

    private MagicDamageCondition() {
    }

    @Override
    public boolean test(DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS) && source.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    @Override
    public MapCodec<MagicDamageCondition> codec() {
        return CODEC;
    }
}
