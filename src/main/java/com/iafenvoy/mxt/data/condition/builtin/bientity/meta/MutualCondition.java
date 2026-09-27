package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Makes a directed bi-entity condition hold in both directions: the counterpart of {@link UndirectedCondition},
 * which takes either one. Asking whether two bodies are friends with each other is the reason it exists.
 */
public record MutualCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<MutualCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(MutualCondition::condition)
    ).apply(i, MutualCondition::new));

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx.target(), ctx) && this.condition.test(ctx.target(), ctx.actor(), ctx);
    }

    @Override
    public @NonNull MapCodec<MutualCondition> codec() {
        return CODEC;
    }
}
