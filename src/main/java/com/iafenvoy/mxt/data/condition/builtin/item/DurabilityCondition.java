package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record DurabilityCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<DurabilityCondition> CODEC = Comparison.CODEC.xmap(DurabilityCondition::new, DurabilityCondition::comparison);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ctx.stack().isDamageableItem() && this.comparison.compare(ctx.stack().getMaxDamage() - ctx.stack().getDamageValue());
    }

    @Override
    public @NonNull MapCodec<DurabilityCondition> codec() {
        return CODEC;
    }
}
