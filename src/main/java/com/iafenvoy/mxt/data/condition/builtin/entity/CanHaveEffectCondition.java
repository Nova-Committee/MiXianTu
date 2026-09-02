package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import org.jspecify.annotations.NonNull;

public record CanHaveEffectCondition(Holder<MobEffect> effect) implements EntityCondition {
    public static final MapCodec<CanHaveEffectCondition> CODEC = MobEffect.CODEC.fieldOf("effect").xmap(CanHaveEffectCondition::new, CanHaveEffectCondition::effect);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof LivingEntity living && CommonHooks.canMobEffectBeApplied(living, new MobEffectInstance(this.effect), null);
    }

    @Override
    public @NonNull MapCodec<CanHaveEffectCondition> codec() {
        return CODEC;
    }
}
