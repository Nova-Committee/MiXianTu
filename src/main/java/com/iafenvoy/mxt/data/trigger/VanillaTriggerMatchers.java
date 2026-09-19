package com.iafenvoy.mxt.data.trigger;

import net.minecraft.advancements.criterion.*;
import net.minecraft.advancements.criterion.ItemDurabilityTrigger.TriggerInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * The payload half of every ported vanilla trigger: each method rebuilds the arguments that trigger's own
 * call site would have passed and asks vanilla's instance the same question. Nothing here reimplements a
 * condition - the matching itself stays vanilla's.
 *
 * <p>Two payload names are shared with the signals MiXianTu already publishes, {@code damage} and
 * {@code blocked}, because vanilla's damage predicates read exactly the numbers its own hooks pass.</p>
 */
public final class VanillaTriggerMatchers {
    private static final String DAMAGE = "damage";
    private static final String BLOCKED = "blocked";
    private static final String DURATION = "duration";

    private VanillaTriggerMatchers() {
    }

    public static boolean consumeItem(ConsumeItemTrigger.TriggerInstance instance, TriggerContext context) {
        return context.item() != null && instance.matches(context.item());
    }

    public static boolean brewedPotion(BrewedPotionTrigger.TriggerInstance instance, TriggerContext context) {
        Holder<Potion> potion = context.payload(TriggerPayload.POTION);
        return potion != null && instance.matches(potion);
    }

    public static boolean tameAnimal(TameAnimalTrigger.TriggerInstance instance, TriggerContext context) {
        LootContext animal = VanillaTriggerSupport.forEntity(context, context.target());
        return animal != null && instance.matches(animal);
    }

    public static boolean bredAnimals(BredAnimalsTrigger.TriggerInstance instance, TriggerContext context) {
        LootContext parent = VanillaTriggerSupport.forEntity(context, context.payload(TriggerPayload.PARENT));
        LootContext partner = VanillaTriggerSupport.forEntity(context, context.payload(TriggerPayload.PARTNER));
        LootContext child = VanillaTriggerSupport.forEntity(context, context.payload(TriggerPayload.CHILD));
        return parent != null && partner != null && instance.matches(parent, partner, child);
    }

    public static boolean villagerTrade(TradeTrigger.TriggerInstance instance, TriggerContext context) {
        LootContext villager = VanillaTriggerSupport.forEntity(context, context.target());
        return villager != null && context.item() != null && instance.matches(villager, context.item());
    }

    public static boolean usedTotem(UsedTotemTrigger.TriggerInstance instance, TriggerContext context) {
        return context.item() != null && instance.matches(context.item());
    }

    public static boolean startedRiding(StartRidingTrigger.TriggerInstance instance, TriggerContext context) {
        return VanillaTriggerSupport.playerPredicate(instance.player(), context);
    }

    public static boolean changedDimension(ChangeDimensionTrigger.TriggerInstance instance, TriggerContext context) {
        ResourceKey<Level> from = context.payload(TriggerPayload.DIMENSION_FROM);
        ResourceKey<Level> to = context.payload(TriggerPayload.DIMENSION_TO);
        return from != null && to != null && instance.matches(from, to);
    }

    public static boolean effectsChanged(EffectsChangedTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        if (player == null) return false;
        Entity source = context.payload(TriggerPayload.EFFECT_SOURCE);
        return instance.matches(player, source == null ? null : VanillaTriggerSupport.forEntity(context, source));
    }

    public static boolean lightningStrike(LightningStrikeTrigger.TriggerInstance instance, TriggerContext context) {
        LootContext lightning = VanillaTriggerSupport.forEntity(context, context.payload(TriggerPayload.LIGHTNING));
        if (lightning == null) return false;
        List<Entity> bystanders = context.payload(TriggerPayload.BYSTANDERS);
        return instance.matches(lightning, VanillaTriggerSupport.contexts(context, bystanders == null ? List.of() : bystanders));
    }

    public static boolean playerHurtEntity(PlayerHurtEntityTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        LootContext victim = VanillaTriggerSupport.forEntity(context, context.target());
        if (player == null || victim == null || context.damageSource() == null) return false;
        float damage = damage(context);
        return instance.matches(player, victim, context.damageSource(), damage, damage, VanillaTriggerSupport.flag(context, BLOCKED));
    }

    public static boolean entityHurtPlayer(EntityHurtPlayerTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        if (player == null || context.damageSource() == null) return false;
        float damage = damage(context);
        return instance.matches(player, context.damageSource(), damage, damage, VanillaTriggerSupport.flag(context, BLOCKED));
    }

    public static boolean playerKilledEntity(KilledTrigger.TriggerInstance instance, TriggerContext context) {
        return killed(instance, context);
    }

    public static boolean entityKilledPlayer(KilledTrigger.TriggerInstance instance, TriggerContext context) {
        return killed(instance, context);
    }

    public static boolean shotCrossbow(ShotCrossbowTrigger.TriggerInstance instance, TriggerContext context) {
        return context.item() != null && instance.matches(context.item());
    }

    public static boolean playerInteractedWithEntity(PlayerInteractTrigger.TriggerInstance instance, TriggerContext context) {
        LootContext interacted = VanillaTriggerSupport.forEntity(context, context.target());
        return interacted != null && context.item() != null && instance.matches(context.item(), interacted);
    }

    public static boolean fishingRodHooked(FishingRodHookedTrigger.TriggerInstance instance, TriggerContext context) {
        LootContext hookedIn = VanillaTriggerSupport.forEntity(context, context.target());
        if (hookedIn == null) return false;
        Collection<ItemStack> items = context.payload(TriggerPayload.ITEMS);
        return context.item() != null && instance.matches(context.item(), hookedIn, items == null ? List.of() : items);
    }

    /**
     * Instances that describe nothing but the player - {@code location} and {@code slept_in_bed} - are answered
     * by the player predicate alone, which {@link VanillaTrigger} evaluates for every ported trigger.
     */
    public static boolean playerOnly(PlayerTrigger.TriggerInstance instance, TriggerContext context) {
        return true;
    }

    public static boolean levitation(LevitationTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        Vec3 start = context.payload(TriggerPayload.START_POSITION);
        if (player == null || start == null) return false;
        return instance.matches(player, start, VanillaTriggerSupport.intValue(context, DURATION));
    }

    public static boolean enterBlock(EnterBlockTrigger.TriggerInstance instance, TriggerContext context) {
        BlockState state = context.payload(TriggerPayload.BLOCK_STATE);
        return state != null && instance.matches(state);
    }

    public static boolean inventoryChanged(InventoryChangeTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        ItemStack changed = context.payload(TriggerPayload.CHANGED_ITEM);
        if (player == null || changed == null) return false;
        Inventory inventory = player.getInventory();
        // Vanilla counts the slots itself and hands the three numbers to the instance, so they are counted
        // here the same way instead of travelling through the payload.
        int full = 0;
        int empty = 0;
        int occupied = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                empty++;
            } else {
                occupied++;
                if (stack.getCount() >= stack.getMaxStackSize()) full++;
            }
        }
        return instance.matches(inventory, changed, full, empty, occupied);
    }

    public static boolean usingItem(UsingItemTrigger.TriggerInstance instance, TriggerContext context) {
        return context.item() != null && instance.matches(context.item());
    }

    public static boolean fallFromHeight(DistanceTrigger.TriggerInstance instance, TriggerContext context) {
        return distance(instance, context);
    }

    public static boolean rideEntityInLava(DistanceTrigger.TriggerInstance instance, TriggerContext context) {
        return distance(instance, context);
    }

    public static boolean netherTravel(DistanceTrigger.TriggerInstance instance, TriggerContext context) {
        return distance(instance, context);
    }

    public static boolean fallAfterExplosion(FallAfterExplosionTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        Vec3 start = context.payload(TriggerPayload.START_POSITION);
        if (player == null || start == null) return false;
        Entity cause = context.payload(TriggerPayload.CAUSE);
        return instance.matches(player.level(), start, player.position(), cause == null ? null : VanillaTriggerSupport.forEntity(context, cause));
    }

    public static boolean thrownItemPickedUpByPlayer(PickedUpItemTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        ItemStack item = context.payload(TriggerPayload.CHANGED_ITEM);
        if (player == null || item == null) return false;
        Entity thrower = context.payload(TriggerPayload.THROWER);
        return instance.matches(player, item, thrower == null ? null : VanillaTriggerSupport.forEntity(context, thrower));
    }

    /**
     * Both kill signals carry the same two objects, so they answer the same question: the victim as the
     * context vanilla builds for it, and the killing blow.
     */
    private static boolean killed(KilledTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        LootContext victim = VanillaTriggerSupport.forEntity(context, context.target());
        if (player == null || victim == null || context.damageSource() == null) return false;
        return instance.matches(player, victim, context.damageSource());
    }

    /**
     * The travelled triggers - {@code fall_from_height}, {@code ride_entity_in_lava} and {@code nether_travel}
     * - share one {@link DistanceTrigger} instance and one question: how far is the player from the place the
     * signal recorded.
     */
    private static boolean distance(DistanceTrigger.TriggerInstance instance, TriggerContext context) {
        ServerPlayer player = VanillaTriggerSupport.player(context);
        Vec3 start = context.payload(TriggerPayload.START_POSITION);
        if (player == null || start == null) return false;
        return instance.matches(player.level(), start, player.position());
    }

    /**
     * Vanilla fires this where a bucket is filled, with the stack that came out of it; the poll sees the empty
     * bucket being replaced, so the filled stack is the payload.
     */
    public static boolean filledBucket(FilledBucketTrigger.TriggerInstance instance, TriggerContext context) {
        ItemStack filled = context.payload(TriggerPayload.CHANGED_ITEM);
        return filled != null && instance.matches(filled);
    }

    /**
     * Vanilla's criterion runs before the new damage value is written, so both its {@code item} predicate and
     * the arithmetic it does read the stack as it was: the previous snapshot is what it is handed, and the new
     * damage value is the number.
     */
    public static boolean itemDurabilityChanged(TriggerInstance instance, TriggerContext context) {
        ItemStack previous = context.payload(TriggerPayload.PREVIOUS_ITEM);
        ItemStack changed = context.payload(TriggerPayload.CHANGED_ITEM);
        return previous != null && changed != null && instance.matches(previous, changed.getDamageValue());
    }

    private static float damage(TriggerContext context) {
        double value = context.formula().explicit(DAMAGE);
        return Double.isFinite(value) ? (float) value : 0.0F;
    }
}
