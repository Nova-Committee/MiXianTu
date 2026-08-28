package com.iafenvoy.mxt.data.resourcebar.builtin.context;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Resource-bar context for the environmental aura template only.
 */
public enum EnvironmentConcentrationContext implements ResourceBarContext {
    INSTANCE;

    @Override
    public Optional<Values> extract(LivingEntity entity, Holder<Resource> resource) {
        if (!entity.level().isClientSide()) return Optional.empty();
        Identifier id = HolderHelper.idOrNull(resource);
        if (id == null) return Optional.empty();
        AuraPool pool = AuraClientState.current().environmentPool(id);
        if (pool.maximum() <= 0.0D && pool.amount() <= 0.0D) return Optional.empty();
        return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
    }

    @Override
    public Component name(Identifier resourceId) {
        return Component.translatable("hud.mxt.resource_bar.environment_concentration",
                DefinitionText.name(resourceId, "resource"));
    }

    @Override
    public Layout layout() {
        return Layout.SELF_HUD;
    }
}
