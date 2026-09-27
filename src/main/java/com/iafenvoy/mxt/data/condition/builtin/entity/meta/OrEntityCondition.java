package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record OrEntityCondition(List<EntityCondition> conditions) implements EntityCondition {
    public static final MapCodec<OrEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrEntityCondition::new, OrEntityCondition::conditions);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return this.conditions.stream().anyMatch(condition -> condition.test(ctx.entity(), ctx));
    }

    @Override
    public @NonNull MapCodec<OrEntityCondition> codec() {
        return CODEC;
    }
}
