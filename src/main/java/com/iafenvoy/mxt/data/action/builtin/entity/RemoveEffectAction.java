package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public record RemoveEffectAction(MobEffect effect) implements EntityAction {
    public static final MapCodec<RemoveEffectAction> CODEC = BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("effect").xmap(RemoveEffectAction::new, RemoveEffectAction::effect);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (ctx.entity() instanceof LivingEntity living)
            living.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this.effect));
    }

    @Override
    public @NonNull MapCodec<RemoveEffectAction> codec() {
        return CODEC;
    }
}
