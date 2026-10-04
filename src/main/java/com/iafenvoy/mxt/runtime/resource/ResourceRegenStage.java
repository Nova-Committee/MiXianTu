package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.EntityTickStages;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.world.entity.LivingEntity;

/**
 * The natural regeneration of every profiled resource a body holds. A value the body's cultivation regenerates
 * itself is left alone: the aura a cultivator breathes in comes out of the ground, not out of this loop.
 */
public final class ResourceRegenStage {
    private ResourceRegenStage() {
    }

    public static void register() {
        EntityTickStages.register("resource_regen", EntityTickStages.AURA_REGEN, ResourceRegenStage::tick);
    }

    private static void tick(LivingEntity entity) {
        ResourceHolderAttachment holder = entity.getExistingData(MxtAttachments.RESOURCE_HOLDER).orElse(null);
        if (holder == null) return;
        // Only profiled values are visited at all: a plain counter is never looked at, and a profiled value
        // with no stored entry yet is created by its first change instead of by this loop.
        for (Reference<Aura> cultivation : MxtDatapackRegistries.holders(entity.level().registryAccess(), MxtResourceKeys.AURA).toList()) {
            Holder<Resource> resource = cultivation.value().resource();
            if (!holder.contains(resource)) continue;
            if (CultivationMethodService.handlesNaturalRegeneration(entity, cultivation)) continue;
            ResourceService.regenerate(holder, resource, cultivation.value().regen(), 1L,
                    ResourceService.formulaContext(entity, resource, FormulaContext.EMPTY));
        }
    }
}
