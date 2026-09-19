package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.data.trigger.TriggerPayload;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes the ported vanilla triggers.
 *
 * <p>Vanilla fires its criteria from call sites no event exposes, but every one of them sits next to a game
 * event that carries the same objects, so the signal is published from there with the payload rebuilt. What
 * is faithful is the signal's meaning and its arguments; what is deliberately not copied is the advancement
 * lifecycle, because a criterion is a one-shot boolean owned by a player and an advancement.</p>
 *
 * <p>Signals are published for the same entity vanilla would have called with - always a player - so a matcher
 * that rebuilds a loot context always has one. The two exceptions are documented at their hook.</p>
 */
@EventBusSubscriber
public final class VanillaTriggerBridge {
    /**
     * The damage numbers vanilla hands to its damage predicates. Vanilla passes the applied damage twice, once
     * as dealt and once as taken, so the same value is reused; the richer NeoForge numbers are published
     * alongside for formulas.
     */
    private static final String DAMAGE = "damage";
    private static final String ORIGINAL_DAMAGE = "original_damage";
    private static final String BLOCKED = "blocked";
    private static final String BLOCKED_DAMAGE = "blocked_damage";
    private static final String USE_DURATION = "use_duration";

    /**
     * Players whose effect set changed during the current tick, mapped to the source vanilla would report for
     * that change. Nothing is remembered while nothing listens to the signal.
     */
    private static final Map<UUID, Entity> PENDING_EFFECTS = new HashMap<>();

    private VanillaTriggerBridge() {
    }

    /**
     * Vanilla fires this from {@code Consumable.onConsume}, so only items that carry the consumable component
     * count - the plain item-use signal already covers every finished use cycle.
     */
    @SubscribeEvent
    public static void onUseItemFinish(Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack used = event.getItem();
        if (!used.has(DataComponents.CONSUMABLE)) return;
        TriggerPublishing.publish(TriggerSignals.CONSUME_ITEM, player,
                FormulaContext.of(player, Map.of(USE_DURATION, (double) event.getDuration())),
                context -> context.item(used));
    }

    @SubscribeEvent
    public static void onBrewedPotion(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Optional<Holder<Potion>> potion = event.getStack()
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
        if (potion.isEmpty()) return;
        TriggerPublishing.publish(TriggerSignals.BREWED_POTION, player, FormulaContext.of(player),
                context -> context.payload(TriggerPayload.POTION, potion.get()));
    }

    /**
     * Vanilla fires this after the animal is tamed; the event fires while the taming is being applied, so an
     * entity predicate that reads the tamed flag itself would see the state just before it flips.
     */
    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)) return;
        Animal animal = event.getAnimal();
        TriggerPublishing.publish(TriggerSignals.TAME_ANIMAL, player, FormulaContext.of(player),
                context -> context.target(animal));
    }

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player cause = event.getCausedByPlayer();
        if (!(cause instanceof ServerPlayer player)) return;
        Mob parent = event.getParentA();
        Mob partner = event.getParentB();
        AgeableMob child = event.getChild();
        TriggerPublishing.publish(TriggerSignals.BRED_ANIMALS, player, FormulaContext.of(player),
                context -> context.payload(TriggerPayload.PARENT, parent)
                        .payload(TriggerPayload.PARTNER, partner)
                        .payload(TriggerPayload.CHILD, child));
    }

    @SubscribeEvent
    public static void onVillagerTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbstractVillager villager = event.getAbstractVillager();
        ItemStack traded = event.getMerchantOffer().getResult();
        TriggerPublishing.publish(TriggerSignals.VILLAGER_TRADE, player, FormulaContext.of(player),
                context -> context.target(villager).item(traded));
    }

    @SubscribeEvent
    public static void onUseTotem(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack totem = event.getTotem();
        TriggerPublishing.publish(TriggerSignals.USED_TOTEM, player, FormulaContext.of(player),
                context -> context.item(totem));
    }

    /**
     * Vanilla publishes this for the players among the ridden entity's passengers. The event fires before the
     * passenger is attached, so the mounting entity and the passengers it already carries are the same set.
     */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide()) return;
        Entity rider = event.getEntityMounting();
        Entity vehicle = event.getEntityBeingMounted();
        List<Entity> riders = new ArrayList<>();
        riders.add(rider);
        rider.getIndirectPassengers().forEach(riders::add);
        for (Entity passenger : riders) {
            if (!(passenger instanceof ServerPlayer player)) continue;
            TriggerPublishing.publish(TriggerSignals.STARTED_RIDING, player, FormulaContext.of(player),
                    context -> context.target(vehicle));
        }
    }

    /**
     * Vanilla fires this after the transfer, inside the player's own dimension-change bookkeeping; the only
     * equivalent hook is the pre-transfer event, so the signal is published one step early.
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceKey<Level> from = player.level().dimension();
        ResourceKey<Level> to = event.getDimension();
        if (from.equals(to)) return;
        TriggerPublishing.publish(TriggerSignals.CHANGED_DIMENSION, player, FormulaContext.of(player),
                context -> context.payload(TriggerPayload.DIMENSION_FROM, from)
                        .payload(TriggerPayload.DIMENSION_TO, to));
    }

    /**
     * Vanilla fires this from the player's own {@code onEffectAdded} / {@code onEffectUpdated} /
     * {@code onEffectsRemoved} overrides, all of which run after the effect map has been written. The events
     * for those changes run one step earlier instead, so the publication is deferred to the end of the tick: a
     * definition's predicate describes the effect set, and reading it early would see the set as it was.
     * Several changes in one tick are reported once.
     */
    @SubscribeEvent
    public static void onEffectAdded(Added event) {
        markEffectsChanged(event.getEntity(), event.getEffectSource());
    }

    @SubscribeEvent
    public static void onEffectRemoved(Remove event) {
        markEffectsChanged(event.getEntity(), null);
    }

    @SubscribeEvent
    public static void onEffectExpired(Expired event) {
        markEffectsChanged(event.getEntity(), null);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_EFFECTS.isEmpty()) return;
        Map<UUID, Entity> pending = new HashMap<>(PENDING_EFFECTS);
        PENDING_EFFECTS.clear();
        for (Entry<UUID, Entity> entry : pending.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            publishEffectsChanged(player, entry.getValue());
        }
    }

    /**
     * Vanilla publishes this to every player within 256 blocks of the strike, with the entities the bolt found
     * around itself as bystanders. The entities already struck are not observable, so a struck entity can also
     * appear as a bystander; everything else follows vanilla's box.
     */
    @SubscribeEvent
    public static void onStruckByLightning(EntityStruckByLightningEvent event) {
        LightningBolt bolt = event.getLightning();
        if (!(bolt.level() instanceof ServerLevel level)) return;
        AABB area = new AABB(bolt.getX() - 15.0D, bolt.getY() - 15.0D, bolt.getZ() - 15.0D,
                bolt.getX() + 15.0D, bolt.getY() + 21.0D, bolt.getZ() + 15.0D);
        List<Entity> bystanders = level.getEntities(bolt, area, entity -> entity.isAlive() && entity != event.getEntity());
        for (ServerPlayer player : level.getPlayers(candidate -> candidate.distanceTo(bolt) < 256.0F)) {
            TriggerPublishing.publish(TriggerSignals.LIGHTNING_STRIKE, player, FormulaContext.of(player),
                    context -> context.payload(TriggerPayload.LIGHTNING, bolt)
                            .payload(TriggerPayload.BYSTANDERS, bystanders));
        }
    }

    /**
     * Vanilla fires both damage criteria from {@code LivingEntity.hurt} with the applied damage twice, so the
     * damage predicates see the same numbers here.
     */
    @SubscribeEvent
    public static void onDamagePost(Post event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Map<String, Double> values = damageValues(event);
        if (victim instanceof ServerPlayer hurtPlayer) {
            TriggerPublishing.publish(TriggerSignals.ENTITY_HURT_PLAYER, hurtPlayer,
                    FormulaContext.of(hurtPlayer, values), context -> context.damageSource(source));
        }
        if (source.getEntity() instanceof ServerPlayer attacker) {
            TriggerPublishing.publish(TriggerSignals.PLAYER_HURT_ENTITY, attacker,
                    FormulaContext.of(attacker, values),
                    context -> context.target(victim).damageSource(source));
        }
    }

    /**
     * Vanilla fires {@code player_killed_entity} for the killer and {@code entity_killed_player} for the dead
     * player, both with the victim described by a context and the killing blow.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer player) {
            TriggerPublishing.publish(TriggerSignals.PLAYER_KILLED_ENTITY, player, FormulaContext.of(player),
                    context -> context.target(victim).damageSource(source));
        }
        if (victim instanceof ServerPlayer player && killer != null) {
            TriggerPublishing.publish(TriggerSignals.ENTITY_KILLED_PLAYER, player, FormulaContext.of(player),
                    context -> context.target(killer).damageSource(source));
        }
    }

    /**
     * Vanilla fires this where a crossbow is released; the arrow event is shared with bows, so the weapon
     * decides.
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack weapon = event.getBow();
        if (!(weapon.getItem() instanceof CrossbowItem)) return;
        TriggerPublishing.publish(TriggerSignals.SHOT_CROSSBOW, player, FormulaContext.of(player),
                context -> context.item(weapon));
    }

    @SubscribeEvent
    public static void onEntityInteract(EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        Entity target = event.getTarget();
        ItemStack held = event.getItemStack();
        TriggerPublishing.publish(TriggerSignals.PLAYER_INTERACTED_WITH_ENTITY, player, FormulaContext.of(player),
                context -> context.target(target).item(held));
    }

    /**
     * Vanilla fires this where the rod is retrieved, once for a hooked entity and once for a loot roll; the
     * event covers the loot roll only, and names the hook instead of the rod, so the rod is looked for in the
     * player's hands.
     */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack rod = heldRod(player);
        if (rod.isEmpty()) return;
        Entity hook = event.getHookEntity();
        List<ItemStack> items = List.copyOf(event.getDrops());
        TriggerPublishing.publish(TriggerSignals.FISHING_ROD_HOOKED, player, FormulaContext.of(player),
                context -> context.item(rod).target(hook).payload(TriggerPayload.ITEMS, items));
    }

    /**
     * Remembers one effect-set change for the end of the tick. A player who is not a server player, or a
     * signal nothing listens to, costs nothing at all.
     */
    private static void markEffectsChanged(LivingEntity entity, @Nullable Entity source) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!TriggerDispatcher.hasListener(TriggerSignals.EFFECTS_CHANGED)) return;
        PENDING_EFFECTS.put(player.getUUID(), source);
    }

    /**
     * Vanilla fires this from {@code ServerPlayer.updateUsingItem}, once per tick while an item is being used;
     * the event is posted from that same method.
     */
    @SubscribeEvent
    public static void onUseItemTick(Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack used = event.getItem();
        TriggerPublishing.publish(TriggerSignals.USING_ITEM, player,
                FormulaContext.of(player, Map.of(USE_DURATION, (double) event.getDuration())),
                context -> context.item(used));
    }

    /**
     * Vanilla fires this from {@code ServerPlayer.onItemPickup} with the picked up stack and the thrower the
     * item entity remembers.
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemEntity picked = event.getItemEntity();
        ItemStack item = event.getOriginalStack();
        Entity thrower = picked.getOwner();
        TriggerPublishing.publish(TriggerSignals.THROWN_ITEM_PICKED_UP_BY_PLAYER, player, FormulaContext.of(player),
                context -> context.payload(TriggerPayload.CHANGED_ITEM, item)
                        .payload(TriggerPayload.THROWER, thrower));
    }

    private static void publishEffectsChanged(ServerPlayer player, @Nullable Entity source) {
        TriggerPublishing.publish(TriggerSignals.EFFECTS_CHANGED, player, FormulaContext.of(player), context -> {
            if (source != null) context.payload(TriggerPayload.EFFECT_SOURCE, source);
        });
    }

    private static Map<String, Double> damageValues(Post event) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(DAMAGE, (double) event.getInflictedDamage());
        values.put(ORIGINAL_DAMAGE, (double) event.getOriginalDamage());
        values.put(BLOCKED_DAMAGE, (double) event.getBlockedDamage());
        values.put(BLOCKED, event.getBlockedDamage() > 0.0F ? 1.0D : 0.0D);
        return values;
    }

    private static ItemStack heldRod(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof FishingRodItem) return main;
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof FishingRodItem ? off : ItemStack.EMPTY;
    }
}
