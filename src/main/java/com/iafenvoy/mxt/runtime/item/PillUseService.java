package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.item.PillItem;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Stop;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Drives the vanilla use cycle for a pill bound to an item that has no use of its own: both the duration and the
 * pose are read off {@code minecraft:consumable}, so one is written on the click and taken off again the moment
 * that gesture ends. Nothing about the item's own definition changes, and no save keeps the component.
 */
@EventBusSubscriber
public final class PillUseService {
    // Whose copy of the cycle this module armed, so the take-back only ever removes what it wrote itself.
    private static final Set<UUID> ARMED = new HashSet<>();

    private PillUseService() {
    }

    // The lowest priority: ItemQualityService refuses an illegal dose at the highest one, and a cancelled event
    // never reaches a lower priority. A hold outranks a pill, so an item that is both is read instead of eaten.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemUse(RightClickItem event) {
        LivingEntity entity = event.getEntity();
        arm(entity, entity.getItemInHand(event.getHand()));
    }

    /**
     * Arms a stack a pill binding claims but whose own item cannot be used: without this the click would fall
     * through the whole of {@code Item#use} and nothing would happen at all. Public like {@code HoldService.arm}
     * and for the same reason - the audit drives a cycle without a client to click.
     */
    public static void arm(LivingEntity entity, ItemStack stack) {
        // Shift asks an item for its other behaviour, and an eating cycle would swallow that click.
        if (stack.isEmpty() || entity.isShiftKeyDown()) return;
        if (stack.has(DataComponents.CONSUMABLE) || stack.getPrototype().has(DataComponents.CONSUMABLE)) return;
        // Swapping, blocking and kinetic weapons all answer the click with their own branch of Item#use, which
        // vanilla reads before the consumable one: arming here would take that use away from the item.
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.swappable()) return;
        if (stack.has(DataComponents.BLOCKS_ATTACKS) || stack.has(DataComponents.KINETIC_WEAPON)) return;
        Provider registries = entity.level().registryAccess();
        if (ItemBindingService.resolvePill(registries, stack).effects().isEmpty()) return;
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold != null && hold.claims(entity, registries, stack)) return;
        stack.set(DataComponents.CONSUMABLE, PillItem.CONSUMABLE);
        ARMED.add(entity.getUUID());
    }

    // Vanilla has eaten one by the time these run, so what is left of the stack is the item as its author wrote it.
    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        takeBack(event.getEntity());
    }

    @SubscribeEvent
    public static void onUseStop(Stop event) {
        takeBack(event.getEntity());
    }

    // A gesture can also end without either event - dying mid-eat is one - so an entity that is no longer using
    // an item gives the component back on its next tick.
    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (ARMED.isEmpty() || !(event.getEntity() instanceof LivingEntity entity) || entity.isUsingItem()) return;
        takeBack(entity);
    }

    private static void takeBack(LivingEntity entity) {
        if (!ARMED.remove(entity.getUUID())) return;
        strip(entity.getMainHandItem());
        strip(entity.getOffhandItem());
    }

    private static void strip(ItemStack stack) {
        if (!stack.isEmpty() && PillItem.CONSUMABLE.equals(stack.get(DataComponents.CONSUMABLE))
                && !stack.getPrototype().has(DataComponents.CONSUMABLE))
            stack.remove(DataComponents.CONSUMABLE);
    }
}
