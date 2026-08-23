package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.registry.MxtDataComponents;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityHolderComponent;
import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.AuraAbilityType;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.event.AbilityTriggerEvent.Post;
import com.iafenvoy.mxt.event.AbilityTriggerEvent.Pre;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Centralizes vanilla-event subscriptions and dispatches only abilities held by the affected entity.
 */
@EventBusSubscriber
public final class AbilityEventBridge {
    private static final ThreadLocal<Set<DispatchKey>> DISPATCHING = ThreadLocal.withInitial(HashSet::new);

    private AbilityEventBridge() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        FormulaContext context = FormulaContext.of(entity, Map.of("damage", (double) event.getInflictedDamage()));
        dispatch("hurt", entity, context, definition -> definition.damageCondition().test(event.getSource(), event.getInflictedDamage(), context));
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        AbilityHolderComponent abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderComponent resourceHolder = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        initializeHudResources(entity, resourceHolder);
        resourceHolder.values().keySet().forEach(resource -> {
            Identifier resourceId = HolderHelper.id(resource);
            Resource definition = resource.value();
            ResourceService.regenerate(resourceHolder, resourceId, definition, 1L,
                    ResourceService.formulaContext(entity, resourceId, definition, FormulaContext.EMPTY));
        });
        dispatch("tick", entity, FormulaContext.of(entity), definition -> true);
        PassiveAttributeService.tick(entity);
        if (entity.level().getGameTime() % 20L == 0L) syncCuriosAbilities(entity, abilities);
        tickAuras(entity, abilities, entity.level().getGameTime());
        finishDueCasts(entity, abilities, resourceHolder, entity.level().getGameTime());
        Holder<Ability> ability = abilities.channelledAbility().orElse(null);
        if (ability == null) return;
        Ability definition = ability.value();
        AbilityService.tickChannel(ability, definition, entity, abilities, resourceHolder,
                entity.level().getGameTime(), FormulaContext.of(entity));
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("target_is_living", event.getTarget() instanceof LivingEntity ? 1.0D : 0.0D);
        if (event.getTarget() instanceof LivingEntity target) values.put("target_health", (double) target.getHealth());
        dispatch("attack", event.getEntity(), FormulaContext.of(event.getEntity(), values), definition -> true);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        FormulaContext victimContext = FormulaContext.of(victim, Map.of("victim_health", Math.max(0.0D, victim.getHealth())));
        dispatch("death", victim, victimContext, definition -> true);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            FormulaContext attackerContext = FormulaContext.of(attacker, Map.of("target_health", Math.max(0.0D, victim.getHealth())));
            dispatch("kill", attacker, attackerContext, definition -> true);
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !ItemQualityService.canUse(entity, event.getItem())) return;
        dispatch("item_use", entity, FormulaContext.of(entity, Map.of("use_duration", (double) event.getDuration())), definition -> true);
    }

    @SubscribeEvent
    public static void onBlockUse(RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        dispatch("block_use", event.getEntity(), blockContext(event.getEntity(), event.getPos()), definition -> true);
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        dispatch("block_break", event.getPlayer(), blockContext(event.getPlayer(), event.getPos()), definition -> true);
    }

    /**
     * Keeps equipment-contributed ability sources in sync before dispatching the equip trigger.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        AbilityHolderComponent holder = entity.getData(MxtAttachments.ABILITY_HOLDER);
        Identifier source = equipmentSource(event.getSlot(), event.getTo());
        itemAbilities(event.getFrom()).stream().map(ability -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, ability))
                .flatMap(Optional::stream).forEach(ability -> holder.revoke(ability, source));
        itemAbilities(event.getTo()).stream().map(ability -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, ability))
                .flatMap(Optional::stream).forEach(ability -> holder.grant(ability, source));
        FormulaContext context = FormulaContext.of(entity, Map.of("equipment_slot", (double) event.getSlot().ordinal()));
        dispatch("equip", entity, context, definition -> true);
    }

    private static List<Identifier> itemAbilities(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        ItemAbilitiesComponent data = stack.getOrDefault(MxtDataComponents.ITEM_ABILITIES.get(), new ItemAbilitiesComponent(List.of()));
        return data.abilities();
    }

    /**
     * Curios equipment participates in the same source-counted ability model.
     */
    private static void syncCuriosAbilities(LivingEntity entity, AbilityHolderComponent holder) {
        Set<Holder<Ability>> current = new LinkedHashSet<>();
        for (ItemStack stack : CuriosIntegration.equipped(entity))
            itemAbilities(stack).stream()
                    .map(ability -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, ability))
                    .flatMap(Optional::stream)
                    .forEach(current::add);
        Identifier source = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "curios_equipment");
        holder.reconcileSource(source, current);
    }

    private static Identifier equipmentSource(EquipmentSlot slot, ItemStack stack) {
        Identifier item = stack.isEmpty() ? Identifier.fromNamespaceAndPath("minecraft", "air") : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "equipment/" + slot.getName() + "/" + item.getNamespace() + "/" + item.getPath());
    }

    /**
     * Called by server-side cultivation entry points after a successful breakthrough.
     */
    public static void onBreakthrough(LivingEntity entity, Identifier target, FormulaContext context) {
        Map<String, Double> values = new LinkedHashMap<>(context.variables());
        values.put("breakthrough", 1.0D);
        dispatch("breakthrough", entity, new FormulaContext(values, context.random()), definition -> true);
    }

    private static FormulaContext blockContext(Entity entity, BlockPos pos) {
        return FormulaContext.of(entity, Map.of("block_x", (double) pos.getX(), "block_y", (double) pos.getY(), "block_z", (double) pos.getZ()));
    }

    /**
     * HUD resources are part of the player's visible baseline state, rather than
     * being created only after an ability happens to spend or restore them.
     */
    private static void initializeHudResources(LivingEntity entity, ResourceHolderComponent holder) {
        if (!(entity instanceof Player)) return;
        MxtDatapackRegistries.holders(MxtResourceKeys.RESOURCE)
                .filter(resource -> !resource.value().bars().isEmpty())
                .forEach(resource -> initializeResource(entity, holder, resource));
    }

    private static void initializeResource(LivingEntity entity, ResourceHolderComponent holder, Reference<Resource> resource) {
        HolderHelper.idOptional(resource).ifPresent(id -> ResourceService.initialize(holder, id, resource.value(), ResourceService.formulaContext(entity, id, resource.value(), FormulaContext.EMPTY)));
    }

    private static void tickAuras(LivingEntity actor, AbilityHolderComponent abilities, long gameTime) {
        for (Holder<Ability> ability : abilities.sources().keySet()) {
            Ability definition = ability.value();
            if (!(definition.type() instanceof AuraAbilityType(
                    NumberProvider interval1,
                    NumberProvider radius1
            )) || !definition.condition().test(actor, FormulaContext.of(actor)))
                continue;
            long dueAt = Math.round(abilities.componentState(ability, "aura_next_tick").map(AbilityComponentState::value).orElse((double) gameTime));
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
                    FormulaContext targetContext = actor instanceof LivingEntity caster && target instanceof LivingEntity livingTarget
                            ? FormulaContexts.forEntities(caster, livingTarget, context.variables()) : context;
                    if (definition.targetCondition().test(actor, target, targetContext))
                        definition.biEntityAction().execute(actor, target, targetContext);
                }
            }
            abilities.setComponentState(ability, "aura_next_tick", AbilityComponentState.initial(Math.addExact(gameTime, Math.max(1L, Math.round(interval))), gameTime));
        }
    }

    private static void finishDueCasts(LivingEntity actor, AbilityHolderComponent abilities, ResourceHolderComponent resources, long gameTime) {
        for (Holder<Ability> ability : abilities.sources().keySet()) {
            if (abilities.componentState(ability, "cast_ends_at").map(AbilityComponentState::value).orElse(Double.MAX_VALUE) > gameTime)
                continue;
            AbilityService.finishCast(ability, ability.value(), actor, abilities, resources, gameTime, FormulaContext.of(actor));
        }
    }

    private static void dispatch(String trigger, LivingEntity entity, FormulaContext context, Predicate<Ability> extraCondition) {
        AbilityHolderComponent abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderComponent resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        long gameTime = entity.level().getGameTime();
        for (Holder<Ability> ability : abilities.sources().keySet()) {
            Identifier abilityId = HolderHelper.id(ability);
            Ability definition = ability.value();
            if (definition.trigger().filter(value -> value.event().equals(trigger)).isEmpty() || !extraCondition.test(definition))
                continue;
            if (!passesTriggerChance(entity, definition, context)) continue;
            DispatchKey key = new DispatchKey(entity.getUUID(), abilityId);
            Set<DispatchKey> active = DISPATCHING.get();
            if (!active.add(key)) continue;
            try {
                if (NeoForge.EVENT_BUS.post(new Pre(entity, abilityId, definition, trigger, context)).isCanceled())
                    continue;
                UseResult result = AbilityService.use(ability, definition, entity, abilities, resources, gameTime, context);
                if (result.committed())
                    NeoForge.EVENT_BUS.post(new Post(entity, abilityId, definition, trigger, context));
            } finally {
                active.remove(key);
                if (active.isEmpty()) DISPATCHING.remove();
            }
        }
    }

    /**
     * A triggered ability's chance is evaluated by the server immediately before dispatch.
     */
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
