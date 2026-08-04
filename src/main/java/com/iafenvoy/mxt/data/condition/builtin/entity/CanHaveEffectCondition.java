package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record CanHaveEffectCondition(Holder<MobEffect> effect) implements EntityCondition {
    public static final MapCodec<CanHaveEffectCondition> CODEC = MobEffect.CODEC.fieldOf("effect").xmap(CanHaveEffectCondition::new, CanHaveEffectCondition::effect);

    @Override
    public boolean test(Entity entity) {
        return entity instanceof LivingEntity living && living.canBeAffected(new MobEffectInstance(this.effect));
    }

    @Override
    public MapCodec<CanHaveEffectCondition> codec() {
        return CODEC;
    }
}
