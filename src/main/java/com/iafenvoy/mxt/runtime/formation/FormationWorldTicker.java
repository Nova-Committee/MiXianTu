package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.event.FormationEvent.Tick;
import com.iafenvoy.mxt.event.FormationEvent.TickEffects;
import com.iafenvoy.mxt.event.FormationEvent.UpkeepFailed;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.formation.FormationService.MaintainResult;
import com.iafenvoy.mxt.runtime.world.FormationAbsorption;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Low-frequency lifecycle dispatcher; it never scans unloaded chunks.
 */
@EventBusSubscriber
public final class FormationWorldTicker {
    /**
     * Ticks between two dispatch passes.
     */
    public static final long PERIOD = 20L;

    private static final FormationStructureValidator VALIDATOR = FormationStructureValidator.STRUCTURE;

    private FormationWorldTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !due(level.getGameTime())) return;
        dispatch(level);
    }

    /**
     * Whether a level tick is a formation tick.
     *
     * <p>This is also the granularity of the enter and exit actions, so it is the single knob that
     * decides how long a formation can go without noticing a change.</p>
     */
    public static boolean due(long gameTime) {
        return gameTime % PERIOD == 0L;
    }

    /**
     * One dispatch pass over every active formation in the level: validate, charge upkeep, run the per-entity
     * actions, then the sweep that releases grants from formations a player has left.
     */
    public static void dispatch(ServerLevel level) {
        FormationWorldAttachment world = level.getData(MxtAttachments.FORMATION_WORLD);
        for (Entry<BlockPos, FormationInstance> entry : world.formations().entrySet()) {
            Optional<Formation> definition = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, entry.getValue().formation());
            if (definition.isEmpty() || !VALIDATOR.matches(level, entry.getKey(), definition.get())) {
                FormationWorldService.deactivate(level, entry.getKey());
                continue;
            }
            // The attachment holds the instance itself, so upkeep and its counter are updated in place:
            // there is no write-back that a later branch could skip and silently drop the payment.
            FormationInstance instance = entry.getValue();
            if (!definition.get().maintenanceCosts().isEmpty() && !chargeUpkeep(level, entry.getKey(), instance, definition.get()))
                continue;
            // Settled and paid for, so observers see it. Cancelling TickEffects skips the work.
            NeoForge.EVENT_BUS.post(new Tick(level, entry.getKey(), instance));
            if (!NeoForge.EVENT_BUS.post(new TickEffects(level, entry.getKey(), instance)).isCanceled()) {
                // Modules run before the definition's own hook here too, for the same reason they do per
                // entity: a hook is the place to react to what the array did.
                FormationActionRunner.perPeriod(level, definition.get(), instance, entry.getKey());
                definition.get().tickAction().execute(level, entry.getKey(), FormulaContext.of(level));
                executeEntityActions(level, entry.getKey(), instance, definition.get());
            }
        }
        for (ServerPlayer player : level.players()) releaseOutside(level, player);
    }

    /**
     * Charges one period of upkeep, and decides what an unpaid period means.
     *
     * @return whether the period may continue; false means the formation was taken down, or a listener
     * cancelled {@link UpkeepFailed} to let it stand through a period it could not pay for
     */
    private static boolean chargeUpkeep(ServerLevel level, BlockPos controller, FormationInstance instance, Formation definition) {
        Entity payer = instance.owner().map(level.getEntities()::get).orElse(null);
        // A formation with a store can pay while its owner is absent, which is most of what storing aura is
        // for; whatever the store cannot cover still fails the period.
        FormulaContext context = payer == null ? FormulaContext.of(level) : FormulaContext.of(payer);
        ResourceHolderAttachment resources = payer == null
                ? new ResourceHolderAttachment()
                : payer.getData(MxtAttachments.RESOURCE_HOLDER);
        // The block emitters standing inside this formation supply it instead of the environment, so what
        // they emit this period pays the upkeep before the store and then the owner are asked for the rest.
        MaintainResult result = FormationService.maintain(instance, definition, resources, context,
                supply(level, controller, instance.radius()));
        if (result.maintained()) return true;
        // Cancelling keeps the formation registered: it pays nothing and does nothing this period, which is
        // the hook for content that wants a formation to survive a lean stretch.
        if (NeoForge.EVENT_BUS.post(new UpkeepFailed(level, controller, instance, Optional.ofNullable(payer),
                Optional.ofNullable(result.failedResource()))).isCanceled())
            return false;
        FormationWorldService.deactivate(level, controller);
        return false;
    }

    /**
     * What the formation's own ground supplies this period: the emitters inside it plus, when the server
     * option allows it, the ambient aura of the position it stands on, summed per resource.
     */
    private static Map<Holder<Aura>, Double> supply(ServerLevel level, BlockPos controller, double radius) {
        return combine(
                FormationAbsorption.absorbedFor(level, controller, radius),
                FormationAbsorption.environmentSupply(level, controller),
                MxtServerConfig.INSTANCE.formations.drawsEnvironment.getValue());
    }

    /**
     * Sums the two supply sources. Split from the lookup so the rule is assertable without a level: the
     * option either adds the ambient aura or leaves the formation with only what its own emitters give it.
     */
    public static Map<Holder<Aura>, Double> combine(Map<Holder<Aura>, Double> absorbed,
                                                    Map<Holder<Aura>, Double> environment,
                                                    boolean drawsEnvironment) {
        Map<Holder<Aura>, Double> supply = new LinkedHashMap<>(absorbed);
        if (!drawsEnvironment) return supply;
        environment.forEach((aura, amount) -> supply.merge(aura, amount, Double::sum));
        return supply;
    }

    /**
     * Runs the per-entity actions for one formation: enter, tick, and exit. An entity the formation does not
     * affect is treated as absent rather than present-but-skipped, which is what makes the exit path release
     * what the formation granted it.
     */
    private static void executeEntityActions(ServerLevel level, BlockPos controller, FormationInstance instance,
                                             Formation definition) {
        double radius = instance.radius();
        double radiusSquared = radius * radius;
        Vec3 center = controller.getCenter();
        FormationCarrier carrier = new FormationCarrier(instance.formation(), controller, radius, instance.owner());
        Identifier source = FormationSources.of(instance.formation());
        // Resolved once per formation rather than per entity: the id outlives the owner logging out and the
        // entity does not, so a manager-level source can still answer for an absent owner.
        UUID ownerId = instance.owner().orElse(null);
        Entity owner = ownerId == null ? null : level.getEntities().get(ownerId);
        Set<UUID> previous = FormationEntityActions.tracked(level, controller);
        Set<UUID> present = new HashSet<>();
        for (Entity entity : level.getEntities(null, AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D))) {
            double distanceSquared = entity.distanceToSqr(center);
            if (distanceSquared > radiusSquared) continue;
            // An entity the formation does not affect is not tracked either: the exit is where a
            // formation-scoped grant is released, and a spared entity must not keep one.
            if (!FormationRelations.affects(definition, ownerId, owner, entity)) continue;
            present.add(entity.getUUID());
            EntityActionContext context = FormationEntityActions.context(entity, carrier, radius, distanceSquared);
            // A freshly activated formation has no previous set, so everything already inside receives an
            // enter action - which is why enter actions must be idempotent.
            if (!previous.contains(entity.getUUID())) definition.entityEnterAction().execute(context);
            // The function modules run before the definition's own hook, so a pack customising the tick
            // sees the state the array left behind rather than the state before it acted.
            FormationActionRunner.perEntity(definition, instance, entity, context, owner, ownerId);
            definition.entityTickAction().execute(context);
        }
        FormationEntityActions.remember(level, controller, present);
        for (UUID departed : previous) {
            if (present.contains(departed)) continue;
            Entity entity = level.getEntities().get(departed);
            if (entity == null) continue;
            FormationEntityActions.release(entity, source);
            definition.entityExitAction()
                    .execute(FormationEntityActions.context(entity, carrier, radius, entity.distanceToSqr(center)));
        }
    }

    /**
     * Releases the formation-scoped grants of a player who is no longer inside that formation. A player can
     * leave without the formation ever seeing it, and the ability attachment is persistent.
     */
    private static void releaseOutside(ServerLevel level, ServerPlayer player) {
        // A player who never held a granted ability cannot owe a release, and without this guard the sweep
        // would build a source identifier for every formation in the level, every period.
        if (player.getExistingData(MxtAttachments.ABILITY_HOLDER).isEmpty()) return;
        BlockPos position = player.blockPosition();
        for (Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet()) {
            FormationInstance formation = entry.getValue();
            if (entry.getKey().distSqr(position) <= formation.radius() * formation.radius()) continue;
            FormationEntityActions.release(player, FormationSources.of(formation.formation()));
        }
    }
}
