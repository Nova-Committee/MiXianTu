package com.iafenvoy.mxt.runtime.ability;

import com.google.common.collect.SetMultimap;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.event.dispatcher.CollectAbilityGrantsEvent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.Sources;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rebuilds every ability a definition grants, and the only place that does: a level change, a lost technique, a
 * dropped spirit root and a released contract all end here, so nothing that granted an ability can be left
 * granting it. What each system wants comes from the {@link CollectAbilityGrantsEvent} it answers, and the whole
 * {@code grant/} ledger is reconciled in one pass against it, so an ability that stays granted never loses its last
 * source in between and keeps the state stored under it.
 */
public final class AbilityGrantService {
    private AbilityGrantService() {
    }

    public static Result recalculate(LivingEntity entity) {
        // Grants are state, so only the server rebuilds them; a client reads what it was sent.
        if (entity.level().isClientSide()) return new Result(0, 0);
        SetMultimap<Identifier, Holder<Ability>> desired = NeoForge.EVENT_BUS.post(new CollectAbilityGrantsEvent(entity)).getDesired();
        int granted = 0;
        int revoked = 0;
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        Set<Identifier> sources = new LinkedHashSet<>(abilities.sources().allSources());
        sources.addAll(desired.keySet());
        for (Identifier source : sources) {
            if (!Sources.isGranted(source)) continue;
            Set<Holder<Ability>> wanted = desired.get(source);
            Set<Holder<Ability>> held = abilities.sources().keysHeldBy(source);
            if (held.equals(wanted)) continue;
            abilities.reconcileSource(source, wanted);
            for (Holder<Ability> ability : wanted) if (!held.contains(ability)) granted++;
            for (Holder<Ability> ability : held) if (!wanted.contains(ability)) revoked++;
        }
        AbilityEventBridge.rebuildTriggerSubscriptions(entity);
        return new Result(granted, revoked);
    }

    public record Result(int granted, int revoked) {
    }
}
