package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Makes a directed bi-entity condition pass in either direction.
 */
public record UndirectedCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<UndirectedCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(UndirectedCondition::condition)
    ).apply(i, UndirectedCondition::new));

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx.target(), ctx) || this.condition.test(ctx.target(), ctx.actor(), ctx);
    }

    @Override
    public @NonNull MapCodec<UndirectedCondition> codec() {
        return CODEC;
    }
}
