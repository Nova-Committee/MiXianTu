package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public record HealthCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<HealthCondition> CODEC = Comparison.CODEC.xmap(HealthCondition::new, HealthCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof LivingEntity living && this.comparison.compare(living.getHealth());
    }

    @Override
    public @NonNull MapCodec<HealthCondition> codec() {
        return CODEC;
    }
}
