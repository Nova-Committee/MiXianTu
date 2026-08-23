package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.attachment.SpiritComponent;
import com.iafenvoy.mxt.attachment.FloatHoldingItemComponent;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Optional;

/**
 * Server-authoritative furnace-style fuel handling for item-provided aura.
 * The currently processed item lives in {@code float_holding_item}; only this
 * service may consume its {@link ItemAuraComponent} state.
 */
@EventBusSubscriber
public final class ItemAuraService {
    private static final double EPSILON = 1.0E-9D;

    private ItemAuraService() {
    }

    public static Optional<Holder<ItemAura>> find(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.ITEM_AURA)
                .filter(holder -> holder.value().entries().stream().anyMatch(entry -> entry.matches(stack)))
                .map(holder -> (Holder<ItemAura>) holder)
                .findFirst();
    }

    public static Optional<Holder<ItemAura>> find(LivingEntity entity, ItemStack stack) {
        return find(entity.level().registryAccess(), stack);
    }

    /**
     * Returns the single resource accepted by this fuel or chargeable item.
     */
    public static Optional<Holder<Resource>> type(Provider access, ItemStack stack) {
        return find(access, stack).map(holder -> holder.value().type());
    }

    /**
     * Resolves the item's resource type against the active server registry access.
     */
    public static Optional<Holder<Resource>> type(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : type(server.registryAccess(), stack);
    }

    /**
     * Resolves an item's whole-unit spirit capacity from its active item-aura definition.
     */
    public static int capacity(Provider access, ItemStack stack, FormulaContext context) {
        return find(access, stack).map(holder -> capacity(holder.value(), context)).orElse(0);
    }

    /**
     * Resolves capacity against the active server's datapack registry.
     */
    public static int capacity(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : capacity(server.registryAccess(), stack, FormulaContext.EMPTY);
    }

    private static int capacity(ItemAura definition, FormulaContext context) {
        double total = definition.aura().evaluate(context);
        if (!Double.isFinite(total) || total <= 0.0D) return 0;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(total);
    }

    /**
     * Advances the currently held fuel by one server tick. A missing fuel item
     * first resumes the smallest partially consumed matching stack, then takes
     * one fresh matching item from the main hand or off hand.
     */
    public static TickResult tick(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources, FormulaContext context) {
        FloatHoldingItemComponent holding = entity.getData(MxtAttachments.FLOAT_HOLDING_ITEM);
        ItemStack item = holding.item();
        Holder<ItemAura> active = activeDefinition(entity, item);
        if (active == null) {
            if (!item.isEmpty()) return TickResult.EMPTY;
            item = loadNextItem(entity, context);
            active = activeDefinition(entity, item);
            if (active == null) return TickResult.EMPTY;
        }

        ItemAuraComponent state = item.get(MxtDataComponents.ITEM_AURA);
        if (state == null || state.remain() <= EPSILON) return TickResult.INVALID;
        double speed = active.value().consumeSpeed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return TickResult.INVALID;

        double consumed = Math.min(state.remain(), speed);
        double remaining = state.remain() - consumed;
        double released = release(entity, spirit, resources, active.value().releaseSpeed().evaluate(context), context);
        if (remaining <= EPSILON) {
            exhaust(entity, item, active, context);
            return new TickResult(consumed, released, true, true);
        }
        item.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(remaining));
        return new TickResult(consumed, released, false, true);
    }

    /**
     * Returns an interrupted fuel item to its owner without consuming it.
     */
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
        if (item.isEmpty() || item.get(MxtDataComponents.ITEM_AURA) == null) return null;
        return find(entity, item).orElse(null);
    }

    private static ItemStack loadNextItem(LivingEntity entity, FormulaContext context) {
        ItemStack item = takeSmallestPartialItem(entity);
        if (item.isEmpty()) item = takeFreshHeldItem(entity);
        if (item.isEmpty()) return ItemStack.EMPTY;

        Holder<ItemAura> definition = find(entity, item).orElse(null);
        if (definition == null) {
            giveItem(entity, item);
            return ItemStack.EMPTY;
        }
        // Reserve the stack before assigning service-owned consumption state.
        FloatHoldingItemComponent holding = entity.getData(MxtAttachments.FLOAT_HOLDING_ITEM);
        holding.set(item);
        if (item.get(MxtDataComponents.ITEM_AURA) == null) {
            double total = definition.value().aura().evaluate(context);
            if (!Double.isFinite(total) || total <= 0.0D) {
                holding.clear();
                giveItem(entity, item);
                return ItemStack.EMPTY;
            }
            if (item.getItem() instanceof SpiritItemAccess access) {
                int capacity = capacity(definition.value(), context);
                access.extract(item, definition.value().type(), 0, false);
                int unavailable = access.extract(item, definition.value().type(), access.getCapacity(item), true);
                int available = capacity - unavailable;
                if (available <= 0) {
                    holding.clear();
                    giveItem(entity, item);
                    return ItemStack.EMPTY;
                }
                item.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(available));
            } else {
                item.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(total));
            }
        }
        return item;
    }

    private static ItemStack takeSmallestPartialItem(LivingEntity entity) {
        if (!(entity instanceof Player player)) return ItemStack.EMPTY;
        int selectedSlot = -1;
        double smallestRemainder = Double.POSITIVE_INFINITY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            ItemAuraComponent state = stack.get(MxtDataComponents.ITEM_AURA);
            if (state == null || state.remain() <= EPSILON || state.remain() >= smallestRemainder || find(entity, stack).isEmpty())
                continue;
            selectedSlot = slot;
            smallestRemainder = state.remain();
        }
        return selectedSlot < 0 ? ItemStack.EMPTY : player.getInventory().removeItem(selectedSlot, 1);
    }

    private static ItemStack takeFreshHeldItem(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        if (mainHand.get(MxtDataComponents.ITEM_AURA) == null && find(entity, mainHand).isPresent())
            return mainHand.split(1);
        ItemStack offHand = entity.getOffhandItem();
        if (offHand.get(MxtDataComponents.ITEM_AURA) == null && find(entity, offHand).isPresent())
            return offHand.split(1);
        return ItemStack.EMPTY;
    }

    private static void exhaust(LivingEntity entity, ItemStack item, Holder<ItemAura> active, FormulaContext context) {
        entity.getData(MxtAttachments.FLOAT_HOLDING_ITEM).clear();
        if (item.getItem() instanceof SpiritItemAccess access) {
            access.extract(item, active.value().type(), Integer.MAX_VALUE, false);
            giveItem(entity, item);
        } else {
            active.value().resultStack().ifPresent(template -> giveItem(entity, template.create()));
        }
        active.value().exhaustedAction().execute(entity, context);
    }

    private static double release(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources, double amount,
                                  FormulaContext context) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        Holder<Resource> resource = spirit.realmStage().map(stage -> stage.value().resource()).orElse(null);
        if (resource == null) return 0.0D;
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

    public record TickResult(double consumed, double released, boolean exhausted, boolean active) {
        private static final TickResult EMPTY = new TickResult(0.0D, 0.0D, false, false);
        private static final TickResult INVALID = new TickResult(0.0D, 0.0D, false, true);
    }
}
