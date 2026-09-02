package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record TimeOfDayCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<TimeOfDayCondition> CODEC = Comparison.CODEC.xmap(TimeOfDayCondition::new, TimeOfDayCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.comparison.compare((int) (entity.level().getOverworldClockTime() % 24_000L));
    }

    @Override
    public @NonNull MapCodec<TimeOfDayCondition> codec() {
        return CODEC;
    }
}
