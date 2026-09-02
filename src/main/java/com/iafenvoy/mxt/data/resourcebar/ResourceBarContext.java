package com.iafenvoy.mxt.data.resourcebar;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Selects the value source and presentation context for a resource bar.
 * Contexts are Java-owned registry entries so datapacks can select them, but
 * cannot introduce client-side rendering behavior through JSON.
 */
public interface ResourceBarContext {
    Codec<ResourceBarContext> CODEC = MxtRegistries.RESOURCE_BAR_CONTEXT.byNameCodec();

    Optional<Values> extract(LivingEntity entity, Holder<Resource> resource);

    Component name(Identifier resourceId);

    Layout layout();

    record Values(double current, double minimum, double maximum, long lastChangedTick) {
    }

    //TODO::Multiple position
    enum Layout {
        SELF_HUD, TARGET_OVERLAY, BOSS_OVERLAY
    }

    /**
     * Common extraction for contexts backed by an entity's resource attachment.
     */
    static Optional<Values> extractStored(LivingEntity entity, Holder<Resource> resource) {
        ResourceHolderAttachment holder = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        if (!holder.contains(resource)) return Optional.empty();
        Audit audit = holder.audit(resource);
        return Optional.of(new Values(holder.get(resource), audit.minSnapshot(), audit.maxSnapshot(), audit.lastChangedTick()));
    }
}
