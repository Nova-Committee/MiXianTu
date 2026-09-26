package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record AndItemCondition(List<ItemCondition> conditions) implements ItemCondition {
    public static final MapCodec<AndItemCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndItemCondition::new, AndItemCondition::conditions);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.holder(), ctx.stack(), ctx));
    }

    @Override
    public @NonNull MapCodec<AndItemCondition> codec() {
        return CODEC;
    }
}
