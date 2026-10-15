package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public record MobEffectCondition(Holder<MobEffect> effect) implements EntityCondition {
    public static final MapCodec<MobEffectCondition> CODEC = MobEffect.CODEC.fieldOf("effect").xmap(MobEffectCondition::new, MobEffectCondition::effect);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && living.hasEffect(this.effect);
    }

    @Override
    public @NonNull MapCodec<MobEffectCondition> codec() {
        return CODEC;
    }
}
