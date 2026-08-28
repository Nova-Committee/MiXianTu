package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record MobEffectCondition(MobEffect effect) implements EntityCondition {
    public static final MapCodec<MobEffectCondition> CODEC = BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("effect").xmap(MobEffectCondition::new, MobEffectCondition::effect);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof LivingEntity living && living.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this.effect));
    }

    @Override
    public MapCodec<MobEffectCondition> codec() {
        return CODEC;
    }
}
