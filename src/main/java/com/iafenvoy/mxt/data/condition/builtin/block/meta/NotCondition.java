package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record NotCondition(BlockCondition condition) implements BlockCondition {
    public static final MapCodec<NotCondition> CODEC = BlockCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return !this.condition.test(ctx.level(), ctx.pos(), ctx);
    }

    @Override
    public @NonNull MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
