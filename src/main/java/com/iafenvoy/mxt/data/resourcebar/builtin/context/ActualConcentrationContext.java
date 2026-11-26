package com.iafenvoy.mxt.data.resourcebar.builtin.context;

import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.data.resourcebar.ResourceBarContext;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Resource-bar context for the fully resolved concentration at the player's position.
 */
public enum ActualConcentrationContext implements ResourceBarContext {
    INSTANCE;

    @Override
    public Optional<Values> extract(LivingEntity entity, Holder<Resource> resource) {
        if (!entity.level().isClientSide()) return Optional.empty();
        AuraPool pool = AuraClientState.current().actualPoolForResource(resource);
        if (pool.maximum() <= 0.0D && pool.amount() <= 0.0D) return Optional.empty();
        return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
    }

    @Override
    public Component name(Holder<Resource> resource) {
        return Component.translatable("hud.mxt.resource_bar.actual_concentration");
    }

    @Override
    public Layout layout() {
        return Layout.SELF_HUD;
    }
}
