package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record AirCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<AirCondition> CODEC = Comparison.CODEC.xmap(AirCondition::new, AirCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.comparison.compare(entity.getAirSupply());
    }

    @Override
    public @NonNull MapCodec<AirCondition> codec() {
        return CODEC;
    }
}
