package com.iafenvoy.mxt.compat;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.BackMode;
import com.iafenvoy.mxt.config.MxtServerConfig.BeltMode;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

public final class CuriosIntegration {
    /**
     * Registers the data-driven automatic acceptance predicates used by the back and belt slots.
     */
    public static void registerPredicates() {
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "back_weapon_auto"), (context, stack) -> acceptsBack(MxtServerConfig.backMode(), context, stack));
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "belt_item_auto"), (context, stack) -> acceptsBelt(MxtServerConfig.beltMode(), context, stack));
    }

    private static boolean acceptsBack(BackMode mode, SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (mode) {
            case MANUAL -> false;
            case ALL -> true;
            case WEAPONS -> ItemBindingService.weapon(context.entity().level().registryAccess(), stack).isPresent();
        };
    }

    private static boolean acceptsBelt(BeltMode mode, SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (mode) {
            case MANUAL -> false;
            case ALL -> true;
            case WEAPONS_ARTIFACTS ->
                    ItemBindingService.weapon(context.entity().level().registryAccess(), stack).isPresent()
                            || ArtifactService.state(stack).archetype().isPresent();
        };
    }

    /**
     * Returns a stable snapshot of every non-empty equipped Curios stack.
     */
    public static List<ItemStack> equipped(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(CuriosIntegration::equipped)
                .orElseGet(List::of);
    }

    private static List<ItemStack> equipped(ICuriosItemHandler inventory) {
        ArrayList<ItemStack> result = new ArrayList<>();
        for (ICurioStacksHandler handler : inventory.getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int index = 0; index < stacks.getSlots(); index++) {
                ItemStack stack = stacks.getStackInSlot(index);
                if (!stack.isEmpty()) result.add(stack.copy());
            }
        }
        return result;
    }

    /**
     * Returns physical back and belt stacks mapped to their render positions.
     */
    public static Map<Place, ItemStack> equippedForRender(LivingEntity entity) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity);
        Map<Place, ItemStack> result = new HashMap<>();
        if (optional.isEmpty()) return result;
        ICuriosItemHandler handler = optional.get();
        Map<String, ICurioStacksHandler> all = handler.getCurios();
        findAndApply(all.get("back_weapon"), result, Place.BACK_RIGHT, Place.BACK_LEFT);
        findAndApply(all.get("belt_item"), result, Place.BELT_RIGHT, Place.BELT_LEFT);
        return result;
    }

    private static void findAndApply(ICurioStacksHandler handler, Map<Place, ItemStack> result, Place... places) {
        if (handler == null) return;
        IDynamicStackHandler stacks = handler.getStacks();
        for (int index = 0; index < stacks.getSlots() && index < places.length; index++) {
            if (!MxtServerConfig.forceRenderSlots() && (!handler.isVisible() || index >= handler.getRenders().size() || !handler.getRenders().get(index)))
                continue;
            ItemStack stack = stacks.getStackInSlot(index);
            if (!stack.isEmpty()) result.put(places[index], stack);
        }
    }

    public enum Place {
        BACK_LEFT, BACK_RIGHT, BELT_LEFT, BELT_RIGHT
    }
}
