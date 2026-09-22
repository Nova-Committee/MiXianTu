package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.NonNull;

/**
 * Vanilla has no dedicated magic tag: both the guardian-thorns and witch-resistance tags have to match.
 */
public final class MagicDamageCondition implements DamageCondition {
    public static final MagicDamageCondition INSTANCE = new MagicDamageCondition();
    public static final MapCodec<MagicDamageCondition> CODEC = MapCodec.unit(() -> INSTANCE);

    private MagicDamageCondition() {
    }

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS) && source.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    @Override
    public @NonNull MapCodec<MagicDamageCondition> codec() {
        return CODEC;
    }
}
