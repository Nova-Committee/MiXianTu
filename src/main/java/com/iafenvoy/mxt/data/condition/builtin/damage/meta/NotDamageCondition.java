package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Negates a nested damage condition.
 */
public record NotDamageCondition(DamageCondition condition) implements DamageCondition {
    public static final MapCodec<NotDamageCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DamageCondition.CODEC.fieldOf("condition").forGetter(NotDamageCondition::condition)
    ).apply(i, NotDamageCondition::new));

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        return !this.condition.test(ctx.source(), ctx.amount(), ctx);
    }

    @Override
    public @NonNull MapCodec<NotDamageCondition> codec() {
        return CODEC;
    }
}
