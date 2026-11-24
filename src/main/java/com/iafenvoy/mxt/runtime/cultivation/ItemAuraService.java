package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.attachment.FloatHoldingItemAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.item.SpiritStoneBagItem;
import com.iafenvoy.mxt.item.SpiritStoneItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.runtime.resource.ResourceService;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-authoritative furnace-style fuel handling for item-provided aura. The currently processed item lives
 * in {@code float_holding_item}; only this service may consume its {@link ItemAuraComponent} state.
 */
@EventBusSubscriber
public final class ItemAuraService {
    private ItemAuraService() {
    }

    public static Optional<Holder<ItemAura>> find(Provider access, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof SpiritStoneBagItem) return Optional.empty();
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.ITEM_AURA),
                Holder::value, stack);
    }

    public static Optional<Holder<ItemAura>> find(LivingEntity entity, ItemStack stack) {
        return find(entity.level().registryAccess(), stack);
    }

    public static Optional<Holder<Aura>> type(Provider access, ItemStack stack) {
        return find(access, stack).map(holder -> holder.value().type());
    }

    public static Optional<Holder<Aura>> type(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : type(server.registryAccess(), stack);
    }

    public static double capacity(Provider access, ItemStack stack, FormulaContext context) {
        return find(access, stack)
                .map(holder -> scale(capacity(holder.value(), context), stack.getCount()))
                .orElse(0.0D);
    }

    public static double capacity(HolderLookup.Provider registries, ItemStack stack) {
        return capacity(registries, stack, FormulaContext.of(registries));
    }

    private static double capacity(ItemAura definition, FormulaContext context) {
        double total = MiscUtil.positive(definition.aura().evaluate(context));
        if (total <= 0.0D) return 0;
        return total;
    }

    public static TickResult tick(LivingEntity entity, ResourceHolderAttachment resources, FormulaContext context) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.CULTIVATION)) {
            FloatHoldingItemAttachment holding = entity.getData(MxtAttachments.FLOAT_HOLDING_ITEM);
            ItemStack item = holding.item();
            Holder<ItemAura> active = activeDefinition(entity, item);
            if (active == null) {
                if (!item.isEmpty()) return TickResult.EMPTY;
                Fuel fuel = loadNextItem(entity, holding, context);
                if (fuel == null) return TickResult.EMPTY;
                item = fuel.stack();
                active = fuel.definition();
            }

            int count = item.getCount();
            double remaining = remainingPerItem(item, active.value(), context);
            if (remaining <= 0.0D) {
                exhaust(entity, item, active, context);
                return new TickResult(0.0D, 0.0D, true, true);
            }
            double speed = MiscUtil.positive(active.value().consumeSpeed().evaluate(context));
            if (speed <= 0.0D) return TickResult.INVALID;

            double next = Math.max(0.0D, remaining - speed);
            if (next == remaining) return TickResult.INVALID;
            if (item.getItem() instanceof SpiritStoneItem) {
                SpiritStoneItem.setStoredPerItem(item, active.value().type(), next);
            } else {
                item.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(next));
            }
            double consumed = remaining - next;
            double released = release(entity, resources, active.value().type(),
                    scale(MiscUtil.positive(active.value().releaseSpeed().evaluate(context)) * (consumed / speed), count), context);
            if (next <= 0.0D) exhaust(entity, item, active, context);
            else holding.markDirty();
            return new TickResult(scale(consumed, count), released, next <= 0.0D, true);
        }
    }

    private static double remainingPerItem(ItemStack item, ItemAura definition, FormulaContext context) {
        if (item.getItem() instanceof SpiritStoneItem)
            return SpiritStoneItem.storedPerItem(item, definition.type(), capacity(definition, context));
        ItemAuraComponent fuel = item.get(MxtDataComponents.ITEM_AURA);
        return fuel == null ? 0.0D : fuel.normalize(item.getCount()).remain();
    }

    public static void returnFloatingItem(LivingEntity entity) {
        ItemStack item = entity.getData(MxtAttachments.FLOAT_HOLDING_ITEM).take();
        if (item.isEmpty()) return;
        giveItem(entity, item);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        returnFloatingItem(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) returnFloatingItem(event.getEntity());
    }

    private static Holder<ItemAura> activeDefinition(LivingEntity entity, ItemStack item) {
        if (item.isEmpty() || (!item.has(MxtDataComponents.ITEM_AURA) && !(item.getItem() instanceof SpiritStoneItem
                && item.has(MxtDataComponents.SPIRIT_STORAGE)))) return null;
        return find(entity, item).orElse(null);
    }

    private static @Nullable Fuel loadNextItem(LivingEntity entity, FloatHoldingItemAttachment holding, FormulaContext context) {
        Fuel bagFuel = takeBagFuel(entity, InteractionHand.MAIN_HAND, context);
        if (bagFuel == null) bagFuel = takeBagFuel(entity, InteractionHand.OFF_HAND, context);
        if (bagFuel != null) {
            holding.set(bagFuel.stack());
            return bagFuel;
        }

        ItemStack item = takeSmallestPartialHeldItem(entity);
        if (item.isEmpty()) item = takeSmallestPartialItem(entity);
        if (item.isEmpty()) item = takeFreshHeldItem(entity);
        if (item.isEmpty()) item = takeFreshInventoryItem(entity);
        if (item.isEmpty()) return null;

        Holder<ItemAura> definition = find(entity, item).orElse(null);
        if (definition == null) {
            giveItem(entity, item);
            return null;
        }
        holding.set(item);
        if (prepareFuel(entity, item, definition.value(), context)) return new Fuel(item, definition);
        holding.clear();
        giveItem(entity, item);
        return null;
    }

    private static @Nullable Fuel takeBagFuel(LivingEntity entity, InteractionHand hand, FormulaContext context) {
        ItemStack bag = entity.getItemInHand(hand);
        if (!(bag.getItem() instanceof SpiritStoneBagItem) || bag.getCount() != 1) return null;
        BundleContents contents = bag.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        for (int index = 0; index < contents.size(); index++) {
            ItemStackTemplate stone = contents.items().get(index);
            if (!(stone.item().value() instanceof SpiritStoneItem)) continue;
            ItemAuraComponent state = stone.get(MxtDataComponents.ITEM_AURA);
            SpiritStorageComponent storage = stone.get(MxtDataComponents.SPIRIT_STORAGE);
            if (state == null && storage != null && storage.isEmpty()) continue;
            ItemStack item = stone.create();
            Holder<ItemAura> definition = find(entity, item).orElse(null);
            if (definition == null || !prepareFuel(entity, item, definition.value(), context)) continue;
            // A number provider may run a script that changes the held stack or its contents.
            if (entity.getItemInHand(hand) != bag || bag.get(DataComponents.BUNDLE_CONTENTS) != contents) return null;
            List<ItemStackTemplate> remaining = new ArrayList<>(contents.items());
            remaining.remove(index);
            bag.set(DataComponents.BUNDLE_CONTENTS, remaining.isEmpty() ? BundleContents.EMPTY : new BundleContents(List.copyOf(remaining)));
            if (entity instanceof Player player) player.getInventory().setChanged();
            return new Fuel(item, definition);
        }
        return null;
    }

    private static boolean prepareFuel(LivingEntity entity, ItemStack item, ItemAura definition, FormulaContext context) {
        ItemAuraComponent state = item.get(MxtDataComponents.ITEM_AURA);
        if (item.getItem() instanceof SpiritStoneItem) {
            double available = remainingPerItem(item, definition, context);
            if (available <= 0.0D) return false;
            SpiritStoneItem.setStoredPerItem(item, definition.type(), available);
            return true;
        }
        if (state != null) return state.remain() > 0.0D;

        double total = MiscUtil.positive(definition.aura().evaluate(context));
        if (total <= 0.0D) return false;
        double available = scale(total, item.getCount());
        if (item.getItem() instanceof ItemAuraAccess access) {
            Provider registries = entity.registryAccess();
            double capacity = access.getCapacity(entity, registries, item, definition.type());
            available = capacity - access.extract(entity, registries, item, definition.type(), capacity, false, true);
            if (available <= 0.0D) return false;
        }
        item.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(available / item.getCount()));
        return true;
    }

    private static ItemStack takeSmallestPartialItem(LivingEntity entity) {
        if (!(entity instanceof Player player)) return ItemStack.EMPTY;
        int selectedSlot = -1;
        double smallestRemainder = Double.POSITIVE_INFINITY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            ItemAuraComponent state = stack.get(MxtDataComponents.ITEM_AURA);
            if (state == null || state.remain() <= 0.0D || fuelTotal(stack, state) >= smallestRemainder || find(entity, stack).isEmpty())
                continue;
            selectedSlot = slot;
            smallestRemainder = fuelTotal(stack, state);
        }
        return selectedSlot < 0 ? ItemStack.EMPTY : player.getInventory().removeItem(selectedSlot,
                player.getInventory().getItem(selectedSlot).getCount());
    }

    private static ItemStack takeSmallestPartialHeldItem(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        ItemStack selected = ItemStack.EMPTY;
        double remainder = Double.POSITIVE_INFINITY;
        if (isPartialFuel(entity, main)) {
            ItemAuraComponent state = main.get(MxtDataComponents.ITEM_AURA);
            assert state != null;
            if (fuelTotal(main, state) < remainder) {
                selected = main;
                remainder = fuelTotal(main, state);
            }
        }
        if (isPartialFuel(entity, off)) {
            ItemAuraComponent state = off.get(MxtDataComponents.ITEM_AURA);
            assert state != null;
            if (fuelTotal(off, state) < remainder) selected = off;
        }
        return selected.isEmpty() ? ItemStack.EMPTY : selected.split(selected.getCount());
    }

    private static boolean isPartialFuel(LivingEntity entity, ItemStack stack) {
        ItemAuraComponent state = stack.get(MxtDataComponents.ITEM_AURA);
        return state != null && state.remain() > 0.0D && find(entity, stack).isPresent();
    }

    private static ItemStack takeFreshHeldItem(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        if (isFreshFuel(mainHand) && find(entity, mainHand).isPresent())
            return mainHand.split(mainHand.getCount());
        ItemStack offHand = entity.getOffhandItem();
        if (isFreshFuel(offHand) && find(entity, offHand).isPresent())
            return offHand.split(offHand.getCount());
        return ItemStack.EMPTY;
    }

    private static ItemStack takeFreshInventoryItem(LivingEntity entity) {
        if (!(entity instanceof Player player)) return ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isFreshFuel(stack) && find(entity, stack).isPresent())
                return player.getInventory().removeItem(slot, stack.getCount());
        }
        return ItemStack.EMPTY;
    }

    private static boolean isFreshFuel(ItemStack stack) {
        ItemAuraComponent state = stack.get(MxtDataComponents.ITEM_AURA);
        if (state != null) return false;
        SpiritStorageComponent storage = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        return !(stack.getItem() instanceof SpiritStoneItem) || storage == null || !storage.isEmpty();
    }

    private static double fuelTotal(ItemStack stack, ItemAuraComponent state) {
        return scale(state.normalize(stack.getCount()).remain(), stack.getCount());
    }

    private static double scale(double value, int count) {
        if (!Double.isFinite(value) || value <= 0.0D || count <= 0) return value;
        double result = value * count;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static void exhaust(LivingEntity entity, ItemStack item, Holder<ItemAura> active, FormulaContext context) {
        entity.getData(MxtAttachments.FLOAT_HOLDING_ITEM).clear();
        if (item.getItem() instanceof SpiritStoneItem) {
            giveItem(entity, item);
        } else if (item.getItem() instanceof ItemAuraAccess access) {
            double capacity = access.getCapacity(entity, entity.registryAccess(), item, active.value().type());
            double available = capacity - access.extract(entity, entity.registryAccess(), item, active.value().type(), capacity, false, true);
            access.extract(entity, entity.registryAccess(), item, active.value().type(), available, true, false);
            item.remove(MxtDataComponents.ITEM_AURA);
            giveItem(entity, item);
        } else {
            active.value().resultStack().ifPresent(template -> {
                ItemStack result = template.create();
                long count = (long) result.getCount() * Math.max(1, item.getCount());
                result.setCount((int) Math.min(Integer.MAX_VALUE, count));
                giveItem(entity, result);
            });
        }
        active.value().exhaustedAction().execute(entity, context);
    }

    private static double release(LivingEntity entity, ResourceHolderAttachment resources, Holder<Aura> aura,
                                  double amount, FormulaContext context) {
        amount = MiscUtil.positive(amount);
        if (amount <= 0.0D) return 0.0D;
        // The pool holds values, so the aura names which value is credited: the one direction the two convert
        // in without a lookup.
        Holder<Resource> resource = aura.value().resource();
        double before = resources.get(resource);
        ResourceService.change(resources, resource, amount, ResourceService.formulaContext(entity, resource, context));
        return Math.max(0.0D, resources.get(resource) - before);
    }

    private static void giveItem(LivingEntity entity, ItemStack item) {
        if (item.isEmpty()) return;
        if (entity instanceof Player player) {
            player.getInventory().placeItemBackInInventory(item);
        } else if (entity.level() instanceof ServerLevel level) {
            entity.spawnAtLocation(level, item);
        }
    }

    private record Fuel(ItemStack stack, Holder<ItemAura> definition) {
    }

    public record TickResult(double consumed, double released, boolean exhausted, boolean active) {
        private static final TickResult EMPTY = new TickResult(0.0D, 0.0D, false, false);
        private static final TickResult INVALID = new TickResult(0.0D, 0.0D, false, true);
    }
}
