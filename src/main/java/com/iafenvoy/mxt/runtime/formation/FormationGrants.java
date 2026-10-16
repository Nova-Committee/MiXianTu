package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * The formation-scoped ability source: granting under it is a module's own business, but releasing it is one
 * question with one answer. Leaving the array, the array coming down, a player walking out of every instance of
 * one definition and a module deciding it no longer wants to affect somebody all end here.
 */
public final class FormationGrants {
    private FormationGrants() {
    }

    // Asked without creating the attachment: most entities that walk through a formation never held a grant.
    public static void release(Entity entity, Identifier source) {
        if (!(entity instanceof LivingEntity living)) return;
        AbilityAttachment abilities = living.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (abilities == null || !abilities.sources().heldBy(source)) return;
        if (abilities.reconcileSource(source, Set.of()))
            AbilityEventBridge.rebuildTriggerSubscriptions(living);
    }
}
