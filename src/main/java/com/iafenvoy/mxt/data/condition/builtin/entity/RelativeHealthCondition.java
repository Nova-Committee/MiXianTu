package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public record RelativeHealthCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<RelativeHealthCondition> CODEC = Comparison.CODEC.xmap(RelativeHealthCondition::new, RelativeHealthCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && this.comparison.compare(living.getHealth() / living.getMaxHealth());
    }

    @Override
    public @NonNull MapCodec<RelativeHealthCondition> codec() {
        return CODEC;
    }
}
