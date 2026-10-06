package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.runtime.ModuleHooks;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds every ability a definition grants, and the only place that does: a level change, a lost technique, a
 * dropped spirit root and a released contract all end here, so nothing that granted an ability can be left
 * granting it. What each system wants comes from the registered {@link EntitySources.Grants}, and the whole
 * {@code grant/} ledger is reconciled in one pass against it, so an ability that stays granted never loses its last
 * source in between and keeps the state stored under it.
 */
public final class AbilityGrantService {
    private AbilityGrantService() {
    }

    public static Result recalculate(LivingEntity entity) {
        // Grants are state, so only the server rebuilds them; a client reads what it was sent.
        if (entity.level().isClientSide()) return new Result(0, 0);
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ProgressionAttachment progress = entity.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        Map<Identifier, Set<Identifier>> desired = new LinkedHashMap<>();
        EntitySources.GrantSink sink = (source, ability) ->
                desired.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(HolderHelper.id(ability));
        for (EntitySources.Grants source : ModuleHooks.all(EntitySources.Grants.class))
            source.collect(entity, progress, sink);
        int granted = 0;
        int revoked = 0;
        Set<Identifier> sources = new LinkedHashSet<>(abilities.sources().allSources());
        sources.addAll(desired.keySet());
        for (Identifier source : sources) {
            if (!Sources.isGranted(source)) continue;
            Set<Identifier> wanted = desired.getOrDefault(source, Set.of());
            Set<Identifier> held = abilities.sources().keysHeldBy(source);
            if (held.equals(wanted)) continue;
            abilities.reconcileSource(source, wanted);
            for (Identifier ability : wanted) if (!held.contains(ability)) granted++;
            for (Identifier ability : held) if (!wanted.contains(ability)) revoked++;
        }
        AbilityEventBridge.rebuildTriggerSubscriptions(entity);
        return new Result(granted, revoked);
    }

    public record Result(int granted, int revoked) {
    }
}
