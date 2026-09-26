package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.aura.SpiritStorageTooltipAppender;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Matches chargeable items whose stored spirit power is below their capacity.
 */
public enum SpiritStorageNotFullCondition implements ItemCondition {
    INSTANCE;

    public static final MapCodec<SpiritStorageNotFullCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return SpiritStorageTooltipAppender.resolveCharge(ctx.holder().level().registryAccess(), ctx.stack(), ctx.formula())
                .map(charge -> charge.stored() < charge.capacity()).orElse(false);
    }

    @Override
    public @NonNull MapCodec<SpiritStorageNotFullCondition> codec() {
        return CODEC;
    }
}
