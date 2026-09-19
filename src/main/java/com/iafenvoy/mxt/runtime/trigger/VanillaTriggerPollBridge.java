package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.data.trigger.TriggerPayload;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The ported vanilla triggers that vanilla itself polls.
 *
 * <p>About a third of vanilla's triggers are never fired from a call site: {@code ServerPlayer} compares
 * something every tick, on landing, or when a fall starts - the block it is inside, its location, levitation,
 * the two falls, lava under a vehicle, the nether round trip, the inventory, the item being used, and sleep.
 * Those are ported by copying that comparison, which is what makes them faithful: the state vanilla keeps in
 * its own fields (the fall start, the lava start, the nether entry position) is kept here instead, and the
 * state vanilla exposes publicly (the impulse position and the explosion cause) is read straight off the
 * player.</p>
 *
 * <p>Everything runs on {@link Post}, which wraps the same method vanilla polls in, and every
 * comparison is skipped unless something listens to its signal, so a player costs nothing while no definition
 * reacts to these.</p>
 */
@EventBusSubscriber
public final class VanillaTriggerPollBridge {
    /**
     * Vanilla asks for a location every second, not every tick.
     */
    private static final int LOCATION_INTERVAL = 20;
    private static final String DURATION = "duration";

    private static final Map<UUID, PollState> STATES = new HashMap<>();

    private VanillaTriggerPollBridge() {
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean enterBlock = TriggerDispatcher.hasListener(TriggerSignals.ENTER_BLOCK);
        boolean location = TriggerDispatcher.hasListener(TriggerSignals.LOCATION);
        boolean levitation = TriggerDispatcher.hasListener(TriggerSignals.LEVITATION);
        boolean fall = TriggerDispatcher.hasListener(TriggerSignals.FALL_FROM_HEIGHT)
                || TriggerDispatcher.hasListener(TriggerSignals.FALL_AFTER_EXPLOSION);
        boolean lava = TriggerDispatcher.hasListener(TriggerSignals.RIDE_ENTITY_IN_LAVA);
        boolean sleeping = TriggerDispatcher.hasListener(TriggerSignals.SLEPT_IN_BED);
        boolean inventory = TriggerDispatcher.hasListener(TriggerSignals.INVENTORY_CHANGED);
        boolean bucket = TriggerDispatcher.hasListener(TriggerSignals.FILLED_BUCKET);
        boolean durability = TriggerDispatcher.hasListener(TriggerSignals.ITEM_DURABILITY_CHANGED);
        if (!(enterBlock || location || levitation || fall || lava || sleeping || inventory || bucket || durability)) {
            return;
        }
        PollState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new PollState());
        if (location && player.tickCount % LOCATION_INTERVAL == 0) {
            TriggerPublishing.publish(TriggerSignals.LOCATION, player, FormulaContext.of(player));
        }
        if (levitation) pollLevitation(player, state);
        if (fall) pollFall(player, state);
        if (lava) pollVehicleLava(player, state);
        if (enterBlock) pollEnterBlock(player, state);
        if (inventory || bucket || durability) pollInventory(player, state, inventory, bucket, durability);
        if (sleeping) pollSleeping(player, state);
    }

    /**
     * Vanilla records where levitation started when the effect is added and clears it when the effect is gone;
     * both are observed here on the first and last tick the effect is present.
     */
    private static void pollLevitation(ServerPlayer player, PollState state) {
        if (!player.hasEffect(MobEffects.LEVITATION)) {
            state.levitating = false;
            state.levitationStart = null;
            return;
        }
        if (!state.levitating) {
            state.levitating = true;
            state.levitationStart = player.position();
            state.levitationStartTick = player.tickCount;
        }
        Vec3 start = state.levitationStart;
        if (start == null) return;
        int duration = player.tickCount - state.levitationStartTick;
        TriggerPublishing.publish(TriggerSignals.LEVITATION, player,
                FormulaContext.of(player, Map.of(DURATION, (double) duration)),
                context -> context.payload(TriggerPayload.START_POSITION, start));
    }

    /**
     * Vanilla's {@code trackStartFallingPosition} and {@code resetFallDistance}, with the impulse position and
     * the explosion cause read from the public fields vanilla itself writes.
     */
    private static void pollFall(ServerPlayer player, PollState state) {
        if (player.fallDistance > 0.0 && state.fallStart == null) {
            state.fallStart = player.position();
            Vec3 impulse = player.currentImpulseImpactPos;
            if (impulse != null && impulse.y <= state.fallStart.y) {
                Entity cause = player.currentExplosionCause;
                TriggerPublishing.publish(TriggerSignals.FALL_AFTER_EXPLOSION, player, FormulaContext.of(player),
                        context -> context.payload(TriggerPayload.START_POSITION, impulse)
                                .payload(TriggerPayload.CAUSE, cause));
            }
        }
        if (player.fallDistance == 0.0 && state.fallStart != null) {
            Vec3 start = state.fallStart;
            state.fallStart = null;
            if (player.getHealth() > 0.0F) {
                TriggerPublishing.publish(TriggerSignals.FALL_FROM_HEIGHT, player, FormulaContext.of(player),
                        context -> context.payload(TriggerPayload.START_POSITION, start));
            }
        }
    }

    /**
     * Vanilla's {@code trackEnteredOrExitedLavaOnVehicle}: the first tick records where the vehicle entered
     * lava, and every tick after that reports the trip from there.
     */
    private static void pollVehicleLava(ServerPlayer player, PollState state) {
        Entity vehicle = player.getVehicle();
        if (vehicle != null && vehicle.isInLava()) {
            if (state.lavaStart == null) {
                state.lavaStart = player.position();
            } else {
                Vec3 start = state.lavaStart;
                TriggerPublishing.publish(TriggerSignals.RIDE_ENTITY_IN_LAVA, player, FormulaContext.of(player),
                        context -> context.payload(TriggerPayload.START_POSITION, start));
            }
        }
        if (state.lavaStart != null && (vehicle == null || !vehicle.isInLava())) {
            state.lavaStart = null;
        }
    }

    /**
     * Vanilla tests the blocks its movement passed through; this reports the blocks the player's box started
     * overlapping since the previous tick, which is the same "walked into something" moment. Fluids count, and
     * blocks without collision of their own - grass, torches - do not, the way vanilla's inside-shape test
     * skips them.
     */
    private static void pollEnterBlock(ServerPlayer player, PollState state) {
        ServerLevel level = player.level();
        AABB box = player.getBoundingBox();
        Set<Long> inside = new HashSet<>();
        BlockPos min = BlockPos.containing(box.minX + 1.0E-4, box.minY + 1.0E-4, box.minZ + 1.0E-4);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir()) continue;
            // Fluids count as something to be inside of; a block without collision of its own - grass, a
            // torch - does not, the way vanilla's inside-shape test skips it.
            if (blockState.getFluidState().isEmpty() && blockState.getCollisionShape(level, pos).isEmpty()) continue;
            if (!box.intersects(new AABB(pos))) continue;
            inside.add(pos.asLong());
        }
        if (!state.insideInitialized) {
            state.insideInitialized = true;
            state.insideBlocks = inside;
            return;
        }
        for (long packed : inside) {
            if (state.insideBlocks.contains(packed)) continue;
            BlockState entered = level.getBlockState(BlockPos.of(packed));
            TriggerPublishing.publish(TriggerSignals.ENTER_BLOCK, player, FormulaContext.of(player),
                    context -> context.payload(TriggerPayload.BLOCK_STATE, entered));
        }
        state.insideBlocks = inside;
    }

    /**
     * Vanilla hears about inventory changes from a container listener and reports the slot that changed; the
     * same slots are found here by comparing against the previous tick, and every changed slot is reported.
     * Two more of its triggers live on that same path - a bucket being filled, and an item losing durability -
     * so they are recognised from the pair of stacks this comparison already holds.
     */
    private static void pollInventory(ServerPlayer player, PollState state, boolean changed, boolean bucket, boolean durability) {
        Inventory inventory = player.getInventory();
        List<ItemStack> current = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            current.add(inventory.getItem(slot).copy());
        }
        if (!state.inventoryInitialized) {
            state.inventoryInitialized = true;
            state.inventory = current;
            return;
        }
        for (int slot = 0; slot < current.size(); slot++) {
            ItemStack now = current.get(slot);
            ItemStack before = slot < state.inventory.size() ? state.inventory.get(slot) : ItemStack.EMPTY;
            if (ItemStack.matches(now, before)) continue;
            if (changed) {
                TriggerPublishing.publish(TriggerSignals.INVENTORY_CHANGED, player, FormulaContext.of(player),
                        context -> context.payload(TriggerPayload.CHANGED_ITEM, now));
            }
            if (bucket && filledBucket(before, now)) {
                TriggerPublishing.publish(TriggerSignals.FILLED_BUCKET, player, FormulaContext.of(player),
                        context -> context.payload(TriggerPayload.CHANGED_ITEM, now));
            }
            if (durability && damaged(now, before)) {
                TriggerPublishing.publish(TriggerSignals.ITEM_DURABILITY_CHANGED, player, FormulaContext.of(player),
                        context -> context.payload(TriggerPayload.CHANGED_ITEM, now)
                                .payload(TriggerPayload.PREVIOUS_ITEM, before));
            }
        }
        state.inventory = current;
    }

    /**
     * Vanilla fires this where a bucket is filled, so the empty bucket it replaced is what the pair of stacks
     * is asked for. A filled bucket that arrives any other way - out of a chest, off the ground - looks the
     * same here, while vanilla would not have reported it.
     */
    private static boolean filledBucket(ItemStack before, ItemStack now) {
        return before.is(Items.BUCKET) && !now.is(Items.BUCKET) && !now.isEmpty();
    }

    /**
     * Vanilla fires this where an item is damaged; a repair is written through another path and does not fire.
     */
    private static boolean damaged(ItemStack now, ItemStack before) {
        return now.isDamageableItem() && now.getItem() == before.getItem()
                && now.getDamageValue() > before.getDamageValue();
    }

    /**
     * Vanilla fires this where the player successfully falls asleep, which is the moment the sleeping flag
     * turns on.
     */
    private static void pollSleeping(ServerPlayer player, PollState state) {
        boolean sleeping = player.isSleeping();
        if (sleeping && !state.sleeping) {
            TriggerPublishing.publish(TriggerSignals.SLEPT_IN_BED, player, FormulaContext.of(player));
        }
        state.sleeping = sleeping;
    }

    /**
     * The state is runtime-only, so a player who leaves takes it with them.
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /**
     * Vanilla records where the player entered the nether and reports the trip when they come back to the
     * overworld; the same walk from the pre-transfer event, which is the only dimension hook available.
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TriggerDispatcher.hasListener(TriggerSignals.NETHER_TRAVEL)) return;
        ResourceKey<Level> to = event.getDimension();
        if (Level.NETHER.equals(to)) {
            STATES.computeIfAbsent(player.getUUID(), ignored -> new PollState()).netherStart = player.position();
            return;
        }
        PollState state = STATES.get(player.getUUID());
        if (state == null || state.netherStart == null)
            return;
        Vec3 start = state.netherStart;
        state.netherStart = null;
        if (!Level.OVERWORLD.equals(to)) return;
        TriggerPublishing.publish(TriggerSignals.NETHER_TRAVEL, player, FormulaContext.of(player),
                context -> context.payload(TriggerPayload.START_POSITION, start));
    }

    private static final class PollState {
        private boolean levitating;
        private Vec3 levitationStart;
        private int levitationStartTick;
        private Vec3 fallStart;
        private Vec3 lavaStart;
        private Vec3 netherStart;
        private Set<Long> insideBlocks = Set.of();
        private boolean insideInitialized;
        private List<ItemStack> inventory = List.of();
        private boolean inventoryInitialized;
        private boolean sleeping;
    }
}
