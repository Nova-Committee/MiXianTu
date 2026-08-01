package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.AbilityType.Aura;
import com.iafenvoy.mxt.data.ability.AbilityType.Triggered;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesData;
import com.iafenvoy.mxt.event.AbilityTriggerEvent;
import com.iafenvoy.mxt.event.AbilityTriggerEvent.Post;
import com.iafenvoy.mxt.event.AbilityTriggerEvent.Pre;
import com.iafenvoy.mxt.integration.CuriosIntegration;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Centralizes vanilla-event subscriptions and dispatches only abilities held by the affected entity.
 */
public final class AbilityEventBridge {
    private static final ThreadLocal<Set<DispatchKey>> DISPATCHING = ThreadLocal.withInitial(HashSet::new);

    private AbilityEventBridge() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        FormulaContext context = new FormulaContext(Map.of("damage", (double) event.getInflictedDamage()));
        dispatch("hurt", entity, context, definition -> definition.damageCondition().test(event.getSource(), event.getInflictedDamage(), context));
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        AbilityHolderData abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderData resourceHolder = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        resourceHolder.values().keySet().forEach(resource -> MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, resource)
                .ifPresent(definition -> ResourceService.regenerate(resourceHolder, resource, definition, 1L, FormulaContext.EMPTY)));
        dispatch("tick", entity, FormulaContext.EMPTY, definition -> true);
        if (entity.level().getGameTime() % 20L == 0L) PassiveAttributeService.reconcile(entity);
        if (entity.level().getGameTime() % 20L == 0L) syncCuriosAbilities(entity, abilities);
        tickAuras(entity, abilities, entity.level().getGameTime());
        finishDueCasts(entity, abilities, resourceHolder, entity.level().getGameTime());
        Identifier abilityId = abilities.channelledAbility().orElse(null);
        if (abilityId == null) return;
        AbilityDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, abilityId).orElse(null);
        if (definition == null) {
            AbilityService.stopChannel(abilities);
            return;
        }
        AbilityService.tickChannel(abilityId, definition, entity, abilities, resourceHolder,
                entity.level().getGameTime(), FormulaContext.EMPTY);
    }

    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("target_is_living", event.getTarget() instanceof LivingEntity ? 1.0D : 0.0D);
        if (event.getTarget() instanceof LivingEntity target) values.put("target_health", (double) target.getHealth());
        dispatch("attack", event.getEntity(), new FormulaContext(values), definition -> true);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        FormulaContext victimContext = new FormulaContext(Map.of("victim_health", Math.max(0.0D, victim.getHealth())));
        dispatch("death", victim, victimContext, definition -> true);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            FormulaContext attackerContext = new FormulaContext(Map.of("target_health", Math.max(0.0D, victim.getHealth())));
            dispatch("kill", attacker, attackerContext, definition -> true);
        }
    }

    public static void onItemUseFinish(Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        dispatch("item_use", entity, new FormulaContext(Map.of("use_duration", (double) event.getDuration())), definition -> true);
    }

    public static void onBlockUse(RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        dispatch("block_use", event.getEntity(), blockContext(event.getPos()), definition -> true);
    }

    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        dispatch("block_break", event.getPlayer(), blockContext(event.getPos()), definition -> true);
    }

    /**
     * Keeps equipment-contributed ability sources in sync before dispatching the equip trigger.
     */
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        AbilityHolderData holder = entity.getData(MxtAttachments.ABILITY_HOLDER);
        Identifier source = equipmentSource(event.getSlot(), event.getTo());
        itemAbilities(event.getFrom()).forEach(ability -> holder.revoke(ability, source));
        itemAbilities(event.getTo()).forEach(ability -> holder.grant(ability, source));
        FormulaContext context = new FormulaContext(Map.of("equipment_slot", (double) event.getSlot().ordinal()));
        dispatch("equip", entity, context, definition -> true);
    }

    private static List<Identifier> itemAbilities(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        ItemAbilitiesData data = stack.getOrDefault(MxtDataComponents.ITEM_ABILITIES.get(), new ItemAbilitiesData(List.of()));
        return data.abilities();
    }

    /** Curios equipment participates in the same source-counted ability model. */
    private static void syncCuriosAbilities(LivingEntity entity, AbilityHolderData holder) {
        Set<Identifier> current = new LinkedHashSet<>();
        for (ItemStack stack : CuriosIntegration.equipped(entity))
            current.addAll(itemAbilities(stack));
        Identifier source = Identifier.fromNamespaceAndPath("mxt", "curios_equipment");
        holder.reconcileSource(source, current);
    }

    private static Identifier equipmentSource(EquipmentSlot slot, ItemStack stack) {
        Identifier item = stack.isEmpty() ? Identifier.fromNamespaceAndPath("minecraft", "air") : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath("mxt", "equipment/" + slot.getName() + "/" + item.getNamespace() + "/" + item.getPath());
    }

    /**
     * Called by server-side cultivation entry points after a successful breakthrough.
     */
    public static void onBreakthrough(LivingEntity entity, Identifier target, FormulaContext context) {
        Map<String, Double> values = new LinkedHashMap<>(context.variables());
        values.put("breakthrough", 1.0D);
        dispatch("breakthrough", entity, new FormulaContext(values), definition -> true);
    }

    private static FormulaContext blockContext(BlockPos pos) {
        return new FormulaContext(Map.of("block_x", (double) pos.getX(), "block_y", (double) pos.getY(), "block_z", (double) pos.getZ()));
    }

    private static void tickAuras(LivingEntity actor, AbilityHolderData abilities, long gameTime) {
        for (Identifier abilityId : abilities.sources().keySet()) {
            AbilityDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, abilityId).orElse(null);
            if (definition == null || !(definition.typedType() instanceof Aura(
                    NumberProvider interval1,
                    NumberProvider radius1
            )) || !definition.condition().test(actor, FormulaContext.EMPTY))
                continue;
            long dueAt = Math.round(abilities.componentState(abilityId, "aura_next_tick").map(AbilityComponentState::value).orElse((double) gameTime));
            if (gameTime < dueAt) continue;
            double interval = interval1.evaluate(FormulaContext.EMPTY);
            double radius = radius1.evaluate(FormulaContext.EMPTY);
            if (!Double.isFinite(interval) || interval <= 0.0D || !Double.isFinite(radius) || radius < 0.0D) continue;
            double radiusSquared = radius * radius;
            for (Entity target : actor.level().getEntities(actor, actor.getBoundingBox().inflate(radius))) {
                double distanceSquared = actor.distanceToSqr(target);
                FormulaContext context = new FormulaContext(Map.of("aura_radius", radius, "distance", Math.sqrt(distanceSquared)));
                if (distanceSquared <= radiusSquared && definition.targetCondition().test(actor, target, context))
                    definition.biEntityAction().execute(actor, target, context);
            }
            abilities.setComponentState(abilityId, "aura_next_tick", AbilityComponentState.initial(Math.addExact(gameTime, Math.max(1L, Math.round(interval))), gameTime));
        }
    }

    private static void finishDueCasts(LivingEntity actor, AbilityHolderData abilities, ResourceHolderData resources, long gameTime) {
        for (Identifier abilityId : abilities.sources().keySet()) {
            if (abilities.componentState(abilityId, "cast_ends_at").map(AbilityComponentState::value).orElse(Double.MAX_VALUE) > gameTime)
                continue;
            MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, abilityId).ifPresent(definition -> AbilityService.finishCast(abilityId, definition, actor, abilities, resources, gameTime, FormulaContext.EMPTY));
        }
    }

    private static void dispatch(String trigger, LivingEntity entity, FormulaContext context, Predicate<AbilityDefinition> extraCondition) {
        AbilityHolderData abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderData resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        long gameTime = entity.level().getGameTime();
        for (Identifier abilityId : abilities.sources().keySet()) {
            AbilityDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, abilityId).orElse(null);
            if (definition == null || definition.trigger().filter(value -> value.event().equals(trigger)).isEmpty() || !extraCondition.test(definition))
                continue;
            if (!passesTriggerChance(entity, definition, context)) continue;
            DispatchKey key = new DispatchKey(entity.getUUID(), abilityId);
            Set<DispatchKey> active = DISPATCHING.get();
            if (!active.add(key)) continue;
            try {
                if (NeoForge.EVENT_BUS.post(new Pre(entity, abilityId, definition, trigger, context)).isCanceled())
                    continue;
                UseResult result = AbilityService.use(abilityId, definition, entity, abilities, resources, gameTime, context);
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
    private static boolean passesTriggerChance(LivingEntity entity, AbilityDefinition definition, FormulaContext context) {
        if (!(definition.typedType() instanceof Triggered triggered)) return true;
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
