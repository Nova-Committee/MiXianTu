package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record ChanceCondition(double chance) implements BiEntityCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return ctx.actor().getRandom().nextDouble() < this.chance;
    }

    @Override
    public @NonNull MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
