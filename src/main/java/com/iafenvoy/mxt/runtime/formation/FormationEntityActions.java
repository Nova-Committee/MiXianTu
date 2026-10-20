package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.Sources;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;

/**
 * The per-entity side of a formation: who was inside it on the last beat, and how a formation-scoped grant is released. It
 * lives outside the ticker because teardown can also be called directly through {@link FormationWorldService}, which
 * would otherwise leave the grants of the entities it was tracking. The presence set is in memory only.
 */
final class FormationEntityActions {
    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<UUID>>> PRESENCE = new HashMap<>();

    private FormationEntityActions() {
    }

    // One source covers every instance of a definition, so an entity leaving one instance still holds the grant while
    // another instance of the same definition reaches it. The instance being released is already gone from the registry
    // by the time the dismantle path asks, which is why the exit path passes the instance it is leaving and the
    // dismantle path does not.
    static boolean coveredElsewhere(ServerLevel level, Entity entity, Identifier definition, @Nullable BlockPos leaving) {
        for (Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet()) {
            if (Objects.equals(entry.getKey(), leaving)) continue;
            FormationInstance instance = entry.getValue();
            if (!instance.formation().equals(definition)) continue;
            if (entity.distanceToSqr(entry.getKey().getCenter()) > instance.radius() * instance.radius()) continue;
            // A sibling in range still covers the spot, but it only covers this entity if it would affect it at all: a
            // spared entity must not keep a grant on the strength of a formation that lets it through.
            Optional<Formation> sibling = MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.FORMATION,
                    instance.formation());
            if (sibling.isPresent() && FormationRelations.affects(sibling.get(), instance.owners(), level, entity))
                return true;
        }
        return false;
    }

    // The one answer to "may this entity keep what that definition handed it": the exit path, the dismantle path and the
    // sweep over players all ask it, so none of them can release a grant another one still owes.
    static void releaseIfUncovered(ServerLevel level, Entity entity, Identifier definition, @Nullable BlockPos leaving) {
        if (!coveredElsewhere(level, entity, definition, leaving))
            FormationGrants.release(entity, Sources.formation(definition));
    }

    // Releases everything the formation handed to the entities it was tracking, runs their leaving beat, then
    // forgets them. The array's own closing beat is not here, because a block action cannot see entities - which is
    // exactly why this runs before it.
    static void releaseTracked(ServerLevel level, BlockPos controller, FormationInstance instance,
                               @Nullable Formation definition) {
        Set<UUID> tracked = forget(level, controller);
        if (tracked.isEmpty()) return;
        FormationCarrier carrier = FormationActionContext.carrier(instance, controller);
        for (UUID id : tracked) {
            // An entity the level can no longer resolve has no attachments left to release, and counts as
            // newly arrived if it comes back.
            Entity entity = level.getEntities().get(id);
            if (entity == null) continue;
            releaseIfUncovered(level, entity, instance.formation(), null);
            if (definition != null)
                FormationActionRunner.inactivate(definition, new FormationActionContext(level, carrier, entity));
        }
    }

    static Set<UUID> tracked(ServerLevel level, BlockPos controller) {
        Map<BlockPos, Set<UUID>> tracked = PRESENCE.get(level.dimension());
        Set<UUID> previous = tracked == null ? null : tracked.get(controller);
        return previous == null ? Set.of() : previous;
    }

    static void remember(ServerLevel level, BlockPos controller, Set<UUID> present) {
        PRESENCE.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(controller, Set.copyOf(present));
    }

    static Set<UUID> forget(ServerLevel level, BlockPos controller) {
        Map<BlockPos, Set<UUID>> tracked = PRESENCE.get(level.dimension());
        if (tracked == null) return Set.of();
        Set<UUID> previous = tracked.remove(controller);
        if (tracked.isEmpty()) PRESENCE.remove(level.dimension());
        return previous == null ? Set.of() : previous;
    }
}
