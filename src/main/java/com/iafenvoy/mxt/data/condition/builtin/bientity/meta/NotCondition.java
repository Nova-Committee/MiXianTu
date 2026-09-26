package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record NotCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<NotCondition> CODEC = BiEntityCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return !this.condition.test(ctx.actor(), ctx.target(), ctx);
    }

    @Override
    public @NonNull MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
