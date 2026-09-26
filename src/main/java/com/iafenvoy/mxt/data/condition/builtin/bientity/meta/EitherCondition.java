package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record EitherCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<EitherCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(EitherCondition::new, EitherCondition::condition);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx) || this.condition.test(ctx.target(), ctx);
    }

    @Override
    public @NonNull MapCodec<EitherCondition> codec() {
        return CODEC;
    }
}
