package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;

public record RemoveEffectAction(MobEffect effect) implements EntityAction {
    public static final MapCodec<RemoveEffectAction> CODEC = BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("effect").xmap(RemoveEffectAction::new, RemoveEffectAction::effect);

    @Override
    public void execute(Entity entity) {
        if (entity instanceof LivingEntity living)
            living.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this.effect));
    }

    @Override
    public MapCodec<RemoveEffectAction> codec() {
        return CODEC;
    }
}
