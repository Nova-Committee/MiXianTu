package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.NonNull;

/**
 * Checks whether the incoming damage belongs to Minecraft's fire damage tag.
 */
public final class FireDamageCondition implements DamageCondition {
    public static final FireDamageCondition INSTANCE = new FireDamageCondition();
    public static final MapCodec<FireDamageCondition> CODEC = MapCodec.unit(() -> INSTANCE);

    private FireDamageCondition() {
    }

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return source.is(DamageTypeTags.IS_FIRE);
    }

    @Override
    public @NonNull MapCodec<FireDamageCondition> codec() {
        return CODEC;
    }
}
