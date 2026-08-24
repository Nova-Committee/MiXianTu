package com.iafenvoy.mxt.data.resourcebar.builtin.context;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public enum BossOverlayContext implements ResourceBarContext {
    INSTANCE;

    @Override
    public Optional<Values> extract(LivingEntity entity, Holder<Resource> resource) {
        return ResourceBarContext.extractStored(entity, resource);
    }

    @Override
    public Component name(Identifier resourceId) {
        return Component.translatable("hud.mxt.resource_bar.boss_overlay", DefinitionText.name(resourceId, "resource"));
    }

    @Override
    public Layout layout() {
        return Layout.BOSS_OVERLAY;
    }

}
