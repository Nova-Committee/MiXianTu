package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record TimeOfDayCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<TimeOfDayCondition> CODEC = Comparison.CODEC.xmap(TimeOfDayCondition::new, TimeOfDayCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return this.comparison.compare((int) (ctx.entity().level().getOverworldClockTime() % 24_000L));
    }

    @Override
    public @NonNull MapCodec<TimeOfDayCondition> codec() {
        return CODEC;
    }
}
