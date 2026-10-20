package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.event.FormationEvent.Tick;
import com.iafenvoy.mxt.event.FormationEvent.TickEffects;
import com.iafenvoy.mxt.event.FormationEvent.UpkeepFailed;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.cultivation.AuraDistributionService;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

import java.util.*;
import java.util.Map.Entry;

/**
 * Formation lifecycle dispatcher. It runs a module beat every {@link FormationIntervals#TICK} ticks and the paying
 * period every {@link FormationIntervals#PERIOD}, so a module can be reached more finely than a bill is charged for.
 * It never scans unloaded chunks.
 */
@EventBusSubscriber
public final class FormationWorldTicker {
    private FormationWorldTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !FormationIntervals.beatDue(level.getGameTime()))
            return;
        dispatch(level, level.getGameTime());
    }

    // One dispatch pass over every active formation: validate, charge upkeep, run the modules, then the sweep that
    // releases grants from formations a player has left. Only the first two are per period.
    public static void dispatch(ServerLevel level, long gameTime) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.FORMATION)) {
            boolean period = FormationIntervals.periodDue(gameTime);
            FormationWorldAttachment world = level.getData(MxtAttachments.FORMATION_WORLD);
            for (Entry<BlockPos, FormationInstance> entry : world.formations().entrySet()) {
                Optional<Formation> definition = MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.FORMATION, entry.getValue().formation());
                // A definition that no longer resolves is taken down at once; a standing structure is re-checked on its
                // own period, because re-reading a template twice a second is not what a finer beat is for.
                if (definition.isEmpty()) {
                    FormationWorldService.deactivate(level, entry.getKey());
                    continue;
                }
                if (period && !FormationStructureValidator.of(definition.get()).matches(level, entry.getKey(), definition.get())) {
                    FormationWorldService.deactivate(level, entry.getKey());
                    continue;
                }
                // The attachment holds the instance itself, so upkeep and its counter are updated in place:
                // there is no write-back that a later branch could skip and silently drop the payment.
                FormationInstance instance = entry.getValue();
                if (period) {
                    if (!definition.get().maintenanceCosts().isEmpty() && !chargeUpkeep(level, entry.getKey(), instance, definition.get()))
                        continue;
                    NeoForge.EVENT_BUS.post(new Tick(level, entry.getKey(), instance));
                }
                // Settled and paid for, so observers see it. Cancelling TickEffects skips the work of this one beat.
                if (NeoForge.EVENT_BUS.post(new TickEffects(level, entry.getKey(), instance)).isCanceled()) continue;
                // The array's own beat first, then the entities it covers: a per-entity module reads the state the
                // array's own modules left behind.
                FormationActionRunner.periodic(definition.get(),
                        FormationActionContext.array(level, entry.getKey(), instance), gameTime);
                executeEntityActions(level, entry.getKey(), instance, definition.get());
            }
            if (period) for (ServerPlayer player : level.players()) releaseOutside(level, player);
        }
    }

    // Returns whether the beat may continue; false means the formation was taken down, or a listener cancelled
    // UpkeepFailed to let it stand through a period it could not pay for. A forgiven period is not a frozen one: it
    // pays nothing, and its remaining beats still run.
    private static boolean chargeUpkeep(ServerLevel level, BlockPos controller, FormationInstance instance, Formation definition) {
        Entity payer = instance.owners().primaryEntity(level);
        // A formation with a store can pay while its owner is absent, which is most of what storing aura is
        // for; whatever the store cannot cover still fails the period.
        FormulaContext context = payer == null ? FormulaContext.of(level) : FormulaContext.of(payer);
        ResourceHolderAttachment resources = payer == null
                ? new ResourceHolderAttachment()
                : payer.getData(MxtAttachments.RESOURCE_HOLDER);
        // The formation draws its share of the chunk stock through the same prepass cultivators use, so what it
        // was granted over this period pays the upkeep before the store and then the owner are asked for the rest.
        Result result = FormationService.maintain(instance, definition, resources, context,
                AuraDistributionService.takeUpkeep(level, controller));
        if (result.isSuccess()) return true;
        // Cancelling keeps the formation registered: it pays nothing and does nothing this period, which is
        // the hook for content that wants a formation to survive a lean stretch.
        if (NeoForge.EVENT_BUS.post(new UpkeepFailed(level, controller, instance, Optional.ofNullable(payer),
                Optional.ofNullable(result.failedResource()))).isCanceled())
            return false;
        FormationWorldService.deactivate(level, controller);
        return false;
    }

    // An entity the formation does not affect is treated as absent rather than present-but-skipped, which is what
    // makes the exit path release what the formation granted it.
    private static void executeEntityActions(ServerLevel level, BlockPos controller, FormationInstance instance,
                                             Formation definition) {
        double radius = instance.radius();
        double radiusSquared = radius * radius;
        Vec3 center = controller.getCenter();
        FormationCarrier carrier = FormationActionContext.carrier(instance, controller);
        Identifier source = Sources.formation(instance.formation());
        // Resolved once per formation rather than per entity: the id outlives the owner logging out and the
        // entity does not, so a manager-level source can still answer for an absent owner.
        FormationOwners owners = instance.owners();
        long gameTime = level.getGameTime();
        Set<UUID> previous = FormationEntityActions.tracked(level, controller);
        Set<UUID> present = new HashSet<>();
        for (Entity entity : level.getEntities(null, AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D))) {
            double distanceSquared = entity.distanceToSqr(center);
            if (distanceSquared > radiusSquared) continue;
            // An entity the formation does not affect is not tracked either: the exit is where a
            // formation-scoped grant is released, and a spared entity must not keep one.
            if (!FormationRelations.affects(definition, owners, level, entity)) continue;
            present.add(entity.getUUID());
            FormationActionContext context = new FormationActionContext(level, carrier, entity);
            // A freshly activated formation has no previous set, so everything already inside receives an
            // arriving beat - which is why one must be idempotent.
            if (!previous.contains(entity.getUUID())) FormationActionRunner.activate(definition, context);
            FormationActionRunner.periodic(definition, context, gameTime);
        }
        FormationEntityActions.remember(level, controller, present);
        for (UUID departed : previous) {
            if (present.contains(departed)) continue;
            Entity entity = level.getEntities().get(departed);
            if (entity == null) continue;
            // A source is one per definition, so leaving one instance says nothing about a second instance of the same
            // definition that still covers this entity. The release is the same question the dismantle path and the
            // player sweep ask, so it goes through the same answer.
            FormationEntityActions.releaseIfUncovered(level, entity, instance.formation(), controller);
            FormationActionRunner.inactivate(definition, new FormationActionContext(level, carrier, entity));
        }
    }

    // A player can leave without the formation ever seeing it, and the ability attachment is persistent.
    private static void releaseOutside(ServerLevel level, ServerPlayer player) {
        // A player who never held a granted ability cannot owe a release, and without this guard the sweep
        // would build a source identifier for every formation in the level, every period.
        if (player.getExistingData(MxtAttachments.ABILITY_HOLDER).isEmpty()) return;
        // One source per definition, so the sweep asks the same question the exit path does - once per definition the
        // player has actually walked out of.
        Set<Identifier> present = new LinkedHashSet<>();
        for (Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet())
            present.add(entry.getValue().formation());
        for (Identifier definition : present)
            FormationEntityActions.releaseIfUncovered(level, player, definition, null);
    }
}
