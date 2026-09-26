package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record NotEntityCondition(EntityCondition condition) implements EntityCondition {
    public static final MapCodec<NotEntityCondition> CODEC = SINGLE_CODEC.fieldOf("condition").xmap(NotEntityCondition::new, NotEntityCondition::condition);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return !this.condition.test(ctx.entity(), ctx);
    }

    @Override
    public @NonNull MapCodec<NotEntityCondition> codec() {
        return CODEC;
    }
}
