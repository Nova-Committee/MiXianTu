package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record AmountCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<AmountCondition> CODEC = Comparison.CODEC.xmap(AmountCondition::new, AmountCondition::comparison);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return this.comparison.compare(ctx.stack().getCount());
    }

    @Override
    public @NonNull MapCodec<AmountCondition> codec() {
        return CODEC;
    }
}
