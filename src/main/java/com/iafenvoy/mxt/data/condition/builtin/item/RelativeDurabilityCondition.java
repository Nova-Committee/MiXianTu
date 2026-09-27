package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record RelativeDurabilityCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<RelativeDurabilityCondition> CODEC = Comparison.CODEC.xmap(RelativeDurabilityCondition::new, RelativeDurabilityCondition::comparison);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ctx.stack().isDamageableItem() && this.comparison.compare((float) (ctx.stack().getMaxDamage() - ctx.stack().getDamageValue()) / ctx.stack().getMaxDamage());
    }

    @Override
    public @NonNull MapCodec<RelativeDurabilityCondition> codec() {
        return CODEC;
    }
}
