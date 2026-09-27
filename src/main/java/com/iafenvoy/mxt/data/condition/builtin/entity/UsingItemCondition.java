package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public enum UsingItemCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<UsingItemCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && living.isUsingItem();
    }

    @Override
    public @NonNull MapCodec<UsingItemCondition> codec() {
        return CODEC;
    }
}
