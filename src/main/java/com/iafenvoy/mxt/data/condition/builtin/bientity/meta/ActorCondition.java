package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record ActorCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<ActorCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(ActorCondition::new, ActorCondition::condition);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx);
    }

    @Override
    public @NonNull MapCodec<ActorCondition> codec() {
        return CODEC;
    }
}
