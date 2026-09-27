package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record NotCondition(ItemCondition condition) implements ItemCondition {
    public static final MapCodec<NotCondition> CODEC = ItemCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return !this.condition.test(ctx.holder(), ctx.stack(), ctx);
    }

    @Override
    public @NonNull MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
