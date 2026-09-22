package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.AuraAbilityType;
import com.iafenvoy.mxt.data.storage.AuraPulse;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.storage.ChargesDataStorage;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.event.AbilityTriggeredEvent.Post;
import com.iafenvoy.mxt.event.AbilityTriggeredEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueMasteryService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerPublishing;
import com.iafenvoy.mxt.runtime.trigger.TriggerRehydrator;
import com.iafenvoy.mxt.runtime.trigger.TriggerRehydrators;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.*;
import java.util.function.Consumer;

/**
 * Centralizes the vanilla-event subscriptions and dispatches only abilities held by the affected entity.
 * Reconciliation is the only thing that registers ability subscriptions: publishing a signal never mutates the
 * index, so a publication reads exactly what the last reconciliation built.
 */
@EventBusSubscriber
public final class AbilityEventBridge {
    // Guards one entity/ability pair against re-entering itself through an effect its own use publishes.
    private static final ThreadLocal<Set<DispatchKey>> DISPATCHING = ThreadLocal.withInitial(HashSet::new);

    static {
        TriggerRehydrators.register(new TriggerRehydrator() {
            @Override
            public String module() {
                return "ability";
            }

            @Override
            public void rehydrate(LivingEntity entity) {
                rebuildTriggerSubscriptions(entity);
            }
        });
    }

    private AbilityEventBridge() {
    }

    // Forces class initialization so the rehydrator is registered before the first server lifecycle event.
    public static void initialize() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        FormulaContext context = FormulaContext.of(entity, Map.of("damage", (double) event.getInflictedDamage()));
        dispatch(TriggerSignals.HURT, entity, context, triggerContext -> triggerContext.damageSource(event.getSource())
                .set("damage", (double) event.getInflictedDamage()));
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
       ResourceHolderAttachment resourceHolder = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        initializeHudResources(entity, resourceHolder);
        // Only profiled values are visited at all: a plain counter is never looked at, and a profiled value
        // with no stored entry yet is created by its first change instead of by this loop.
        for (Reference<Aura> cultivation : MxtDatapackRegistries.holders(entity.level().registryAccess(), MxtResourceKeys.AURA).toList()) {
            Holder<Resource> resource = cultivation.value().resource();
            if (!resourceHolder.contains(resource)) continue;
            if (CultivationActionService.handlesNaturalRegeneration(entity, cultivation)) continue;
            ResourceService.regenerate(resourceHolder, resource, cultivation.value().regen(), 1L,
                    ResourceService.formulaContext(entity, resource, FormulaContext.EMPTY));
        }
        dispatch(TriggerSignals.TICK, entity, FormulaContext.of(entity));
        PassiveAttributeService.tick(entity);
        rechargeCharges(entity, abilities, entity.level().getGameTime());
        if (entity.level().getGameTime() % 20L == 0L) {
            // Curios is reconciled on a slow cadence, so the index has to follow it here: it is no longer
            // rebuilt as a side effect of the next publication.
            if (syncCuriosAbilities(entity, abilities)) rebuildTriggerSubscriptions(entity);
            // Mastery is measured by a stored value, so it is re-read on the same slow cadence.
            TechniqueMasteryService.tick(entity);
        }
        tickAuras(entity, abilities, entity.level().getGameTime());
        finishDueCasts(entity, abilities, resourceHolder, entity.level().getGameTime());
        Holder<Ability> ability = abilities.channelledAbility().orElse(null);
        if (ability != null) {
            Ability definition = ability.value();
            AbilityService.tickChannel(ability, definition, entity, abilities, resourceHolder,
                    entity.level().getGameTime(), FormulaContext.of(entity));
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("target_is_living", event.getTarget() instanceof LivingEntity ? 1.0D : 0.0D);
        if (event.getTarget() instanceof LivingEntity target) values.put("target_health", (double) target.getHealth());
        dispatch(TriggerSignals.ATTACK, event.getEntity(), FormulaContext.of(event.getEntity(), values),
                triggerContext -> {
                    triggerContext.target(event.getTarget());
                    triggerContext.set("target_is_living", values.get("target_is_living"));
                    triggerContext.set("target_health", values.getOrDefault("target_health", 0.0D));
                });
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        FormulaContext victimContext = FormulaContext.of(victim, Map.of("victim_health", Math.max(0.0D, victim.getHealth())));
        dispatch(TriggerSignals.DEATH, victim, victimContext);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            FormulaContext attackerContext = FormulaContext.of(attacker, Map.of("target_health", Math.max(0.0D, victim.getHealth())));
            dispatch(TriggerSignals.KILL, attacker, attackerContext,
                    triggerContext -> {
                        triggerContext.target(victim);
                        triggerContext.set("target_health", Math.max(0.0D, victim.getHealth()));
                    });
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !ItemQualityService.canUse(entity, event.getItem())) return;
        dispatch(TriggerSignals.ITEM_USE, entity, FormulaContext.of(entity, Map.of("use_duration", (double) event.getDuration())),
                triggerContext -> {
                    triggerContext.item(event.getItem());
                    triggerContext.set("use_duration", (double) event.getDuration());
                });
    }

    @SubscribeEvent
    public static void onBlockUse(RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        dispatch(TriggerSignals.BLOCK_USE, event.getEntity(), blockContext(event.getEntity(), event.getPos()),
                triggerContext -> triggerContext.position(event.getPos())
                        .block(event.getLevel().getBlockState(event.getPos())));
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        dispatch(TriggerSignals.BLOCK_BREAK, event.getPlayer(), blockContext(event.getPlayer(), event.getPos()),
                triggerContext -> triggerContext.position(event.getPos())
                        .block(event.getLevel().getBlockState(event.getPos())));
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        AbilityAttachment holder = entity.getData(MxtAttachments.ABILITY_HOLDER);
        Identifier source = AbilitySources.equipment(event.getSlot(), event.getTo());
        itemAbilities(entity, event.getFrom()).stream().map(ability -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, ability))
                .flatMap(Optional::stream)
                .forEach(ability -> holder.revoke(ability, source));
        itemAbilities(entity, event.getTo()).stream().map(ability -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, ability))
                .flatMap(Optional::stream).forEach(ability -> holder.grant(ability, source));
        rebuildTriggerSubscriptions(entity);
        FormulaContext context = FormulaContext.of(entity, Map.of("equipment_slot", (double) event.getSlot().ordinal()));
        dispatch(TriggerSignals.EQUIP, entity, context,
                triggerContext -> triggerContext.item(event.getTo())
                        .set("equipment_slot", (double) event.getSlot().ordinal()));
    }

    // The artifact definition is resolved here rather than where the stack was made, so an artifact grants its
    // skills by being held.
    private static List<Identifier> itemAbilities(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        return ArtifactService.abilityIds(entity.level().registryAccess(), stack);
    }

    // Curios gear counts in the same source-counted ability model.
    private static boolean syncCuriosAbilities(LivingEntity entity, AbilityAttachment holder) {
        Set<Holder<Ability>> current = new LinkedHashSet<>();
        for (ItemStack stack : CuriosIntegration.equipped(entity))
            itemAbilities(entity, stack).stream()
                    .map(ability -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, ability))
                    .flatMap(Optional::stream)
                    .forEach(current::add);
        return holder.reconcileSource(AbilitySources.CURIOS, current);
    }

    // Called by the server-side cultivation entry points after a successful breakthrough.
    public static void onBreakthrough(LivingEntity entity, Identifier target, FormulaContext context) {
        dispatch(TriggerSignals.BREAKTHROUGH, entity, context.with("breakthrough", 1.0D));
    }

    private static FormulaContext blockContext(Entity entity, BlockPos pos) {
        return FormulaContext.of(entity, Map.of("block_x", (double) pos.getX(), "block_y", (double) pos.getY(), "block_z", (double) pos.getZ()));
    }

    // HUD resources are part of the player's visible baseline state, rather than being created only after an
    // ability happens to spend or restore them.
    private static boolean initializeHudResources(LivingEntity entity, ResourceHolderAttachment holder) {
        if (!(entity instanceof Player)) return false;
        return MxtDatapackRegistries.holders(MxtResourceKeys.RESOURCE)
                .filter(resource -> !resource.value().bars().isEmpty())
                .anyMatch(resource -> initializeResource(entity, holder, resource));
    }

    private static boolean initializeResource(LivingEntity entity, ResourceHolderAttachment holder, Reference<Resource> resource) {
        return HolderHelper.idOptional(resource).map(id -> ResourceService.initialize(holder, id, resource.value(),
                ResourceService.formulaContext(entity, id, resource.value(), FormulaContext.EMPTY)).changed()).orElse(false);
    }

    // Only an ability that declares mxt:charges is looked at, and AbilityStorage.recharge writes only when a step
    // is actually due, so an entity holding no such ability costs one scan of its grants per tick.
    private static void rechargeCharges(LivingEntity actor, AbilityAttachment abilities, long gameTime) {
        for (Holder<Ability> ability : abilities.sources().keys()) {
            ChargesDataStorage declaration = ability.value().storages().stream()
                    .filter(ChargesDataStorage.class::isInstance)
                    .map(ChargesDataStorage.class::cast)
                    .findFirst().orElse(null);
            if (declaration == null) continue;
            AbilityStorage.recharge(abilities, ability, declaration, gameTime, FormulaContext.of(actor));
        }
    }

    private static boolean tickAuras(LivingEntity actor, AbilityAttachment abilities, long gameTime) {
        boolean changed = false;
        for (Holder<Ability> ability : abilities.sources().keys()) {
            Ability definition = ability.value();
            if (!(definition.type() instanceof AuraAbilityType(
                    NumberProvider interval1,
                    NumberProvider radius1
            )) || !definition.condition().test(actor, FormulaContext.of(actor)))
                continue;
            long dueAt = Math.round(AbilityStorage.get(abilities, ability, AuraPulse.class).map(AuraPulse::nextTick).orElse((double) gameTime));
            if (gameTime < dueAt) continue;
            FormulaContext actorContext = FormulaContext.of(actor);
            double interval = interval1.evaluate(actorContext);
            double radius = radius1.evaluate(actorContext);
            if (!Double.isFinite(interval) || interval <= 0.0D || !Double.isFinite(radius) || radius < 0.0D) continue;
            double radiusSquared = radius * radius;
            for (Entity target : actor.level().getEntities(actor, actor.getBoundingBox().inflate(radius))) {
                double distanceSquared = actor.distanceToSqr(target);
                FormulaContext context = FormulaContext.of(actor, Map.of("aura_radius", radius, "distance", Math.sqrt(distanceSquared)));
                if (distanceSquared <= radiusSquared) {
                    FormulaContext targetContext = target instanceof LivingEntity livingTarget ? FormulaContexts.forEntities(actor, livingTarget, context) : context;
                    AbilityService.executeTargetAction(definition, actor, target, targetContext);
                }
            }
            AbilityStorage.set(abilities, ability,
                    new AuraPulse(Math.addExact(gameTime, Math.max(1L, Math.round(interval)))), gameTime);
            changed = true;
        }
        return changed;
    }

    private static boolean finishDueCasts(LivingEntity actor, AbilityAttachment abilities,
                                          ResourceHolderAttachment resources, long gameTime) {
        boolean changed = false;
        for (Holder<Ability> ability : abilities.sources().keys()) {
            if (!AbilityStorage.castDue(abilities, ability, gameTime)) continue;
            AbilityService.finishCast(ability, ability.value(), actor, abilities, resources, gameTime, FormulaContext.of(actor));
            changed = true;
        }
        return changed;
    }

    private static void dispatch(Identifier signalType, LivingEntity entity, FormulaContext context) {
        dispatch(signalType, entity, context, ignored -> {
        });
    }

    private static void dispatch(Identifier signalType, LivingEntity entity, FormulaContext context,
                                 Consumer<TriggerContext> enrich) {
        TriggerPublishing.publish(signalType, entity, context, enrich);
    }

    // Intentionally idempotent, and can be called after datapack reloads or source reconciliation.
    public static void rebuildTriggerSubscriptions(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        syncAbilitySubscriptions(entity);
    }

    private static void syncAbilitySubscriptions(LivingEntity entity) {
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        TriggerDispatcher.clearModule(entity.getUUID(), "ability");
        for (Holder<Ability> ability : abilities.sources().keys()) {
            Identifier abilityId = HolderHelper.id(ability);
            Ability definition = ability.value();
            int triggerIndex = 0;
            for (Trigger trigger : definition.triggers()) {
                String identity = abilityId + "/" + triggerIndex++;
                TriggerDispatcher.register(new TriggerSubscription(entity.getUUID(), "ability", identity,
                        trigger, signal -> true,
                        signal -> {
                            FormulaContext formula = signal.context().formula();
                            // A damage condition belongs to the hurt signal that carries it, so it is read from
                            // the signal instead of being baked into the subscription by the publisher.
                            if (TriggerSignals.HURT.equals(signal.type())
                                    && !definition.damageCondition().test(signal.context().damageSource(),
                                    (float) formula.value("damage"), signal.context()))
                                return;
                            if (!passesTriggerChance(entity, definition, formula)) return;
                            Set<DispatchKey> active = DISPATCHING.get();
                            DispatchKey key = new DispatchKey(entity.getUUID(), abilityId);
                            if (!active.add(key)) return;
                            try {
                                if (NeoForge.EVENT_BUS.post(new Pre(entity, ability, signal.type(), signal.context())).isCanceled())
                                    return;
                                UseResult result = AbilityService.use(ability, definition, entity, abilities, resources, signal.gameTime(), formula);
                                if (result.committed())
                                    NeoForge.EVENT_BUS.post(new Post(entity, ability, signal.type(), signal.context()));
                            } finally {
                                active.remove(key);
                            }
                        }, false));
            }
        }
    }

    // A triggered ability's chance is evaluated by the server immediately before dispatch.
    private static boolean passesTriggerChance(LivingEntity entity, Ability definition, FormulaContext context) {
        if (!(definition.type() instanceof TriggeredAbilityType triggered)) return true;
        final double chance;
        try {
            chance = triggered.chance().evaluate(context);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!Double.isFinite(chance) || chance <= 0.0D) return false;
        return chance >= 1.0D || entity.getRandom().nextDouble() < chance;
    }

    private record DispatchKey(UUID entity, Identifier ability) {
    }
}
