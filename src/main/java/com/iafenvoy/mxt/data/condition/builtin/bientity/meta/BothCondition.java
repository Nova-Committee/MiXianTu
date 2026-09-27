package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record BothCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<BothCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(BothCondition::new, BothCondition::condition);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx) && this.condition.test(ctx.target(), ctx);
    }

    @Override
    public @NonNull MapCodec<BothCondition> codec() {
        return CODEC;
    }
}
