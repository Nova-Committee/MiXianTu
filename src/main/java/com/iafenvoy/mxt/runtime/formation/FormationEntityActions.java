package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The per-entity side of a formation: who was inside it last tick, the context each per-entity action
 * receives, and how a formation-scoped grant is released.
 *
 * <p>This lives outside the ticker because teardown runs from {@link FormationWorldService}, which is
 * the one path every caller takes — the ticker, the plate, and any future command. Releasing from the
 * ticker instead would leave a direct teardown call still holding on to the grants of the entities it
 * was tracking, which is exactly what the server audit caught.</p>
 *
 * <p>The presence set is in memory only and keyed by dimension rather than by level instance, so an
 * unloaded level cannot leave an entry behind. Not persisting it is deliberate: presence is transient,
 * and a restart treating everyone inside as newly arrived is the correct answer for an idempotent
 * enter action.</p>
 */
final class FormationEntityActions {
    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<UUID>>> PRESENCE = new HashMap<>();

    private FormationEntityActions() {
    }

    /**
     * The context a per-entity action receives: the numbers a formula can read as explicit values,
     * plus the formation itself as {@link FormationCarrier} extension data.
     */
    static EntityActionContext context(Entity entity, FormationCarrier carrier, double radius, double distanceSquared) {
        Vec3 center = carrier.center();
        FormulaContext formula = FormulaContext.of(entity, Map.of(
                "formation_radius", radius,
                "distance", Math.sqrt(distanceSquared),
                "formation_x", center.x,
                "formation_y", center.y,
                "formation_z", center.z
        ));
        EntityActionContext context = new EntityActionContext(entity, formula);
        context.set(FormationCarrier.KEY, carrier);
        return context;
    }

    /**
     * Drops every ability the source granted. Narrow by construction: a source a content pack never
     * used matches nothing, so ignoring the convention costs one map scan.
     *
     * <p>Reads the attachment without creating it: most entities that walk through a formation never
     * held a granted ability, and materialising an attachment for each of them would be pure waste.</p>
     */
    static void release(Entity entity, Identifier source) {
        if (!(entity instanceof LivingEntity living)) return;
        AbilityAttachment abilities = living.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (abilities == null || !abilities.sources().containsValue(source)) return;
        if (abilities.reconcileSource(source, Set.of())) AbilityEventBridge.rebuildTriggerSubscriptions(living);
    }

    /**
     * Releases everything the formation handed to the entities it was tracking, then forgets them.
     *
     * <p>Runs the exit action as well, which is the only chance those entities get: the formation's
     * own {@code deactivate_action} is a block action whose context is a {@link Level} and cannot
     * reach them.</p>
     */
    static void releaseTracked(ServerLevel level, BlockPos controller, FormationInstance instance) {
        Set<UUID> tracked = forget(level, controller);
        if (tracked.isEmpty()) return;
        double radius = instance.radius();
        FormationCarrier carrier = new FormationCarrier(instance.formation(), controller, radius, instance.owner());
        Identifier source = FormationSources.of(instance.formation());
        EntityAction exit = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, instance.formation())
                .map(Formation::entityExitAction).orElse(NoOpAction.INSTANCE);
        for (UUID id : tracked) {
            // An entity the level can no longer resolve — unloaded chunk, logged out — has no
            // attachments left to release, so both the revoke and the exit action are skipped for it.
            // It is treated as newly arrived if it comes back, which is why enter actions are
            // documented as needing to be idempotent.
            Entity entity = level.getEntities().get(id);
            if (entity == null) continue;
            release(entity, source);
            exit.execute(context(entity, carrier, radius, entity.distanceToSqr(carrier.center())));
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

    /**
     * Removes and returns what the formation was tracking.
     */
    static Set<UUID> forget(ServerLevel level, BlockPos controller) {
        Map<BlockPos, Set<UUID>> tracked = PRESENCE.get(level.dimension());
        if (tracked == null) return Set.of();
        Set<UUID> previous = tracked.remove(controller);
        if (tracked.isEmpty()) PRESENCE.remove(level.dimension());
        return previous == null ? Set.of() : previous;
    }
}
