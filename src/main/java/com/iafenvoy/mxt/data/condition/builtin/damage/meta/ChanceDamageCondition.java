package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.NonNull;

/**
 * Randomly passes using the source entity's random stream when available.
 */
public record ChanceDamageCondition(double chance) implements DamageCondition {
    public static final MapCodec<ChanceDamageCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceDamageCondition::new, ChanceDamageCondition::chance);

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return source.getEntity() != null ? source.getEntity().getRandom().nextDouble() < this.chance : RandomSource.create().nextDouble() < this.chance;
    }

    @Override
    public @NonNull MapCodec<ChanceDamageCondition> codec() {
        return CODEC;
    }
}
