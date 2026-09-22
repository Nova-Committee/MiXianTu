package com.iafenvoy.mxt.compat;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.BackMode;
import com.iafenvoy.mxt.config.MxtServerConfig.BeltMode;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.*;

public final class CuriosIntegration {
    public static void registerPredicates() {
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "back_weapon_auto"), (context, stack) -> acceptsBack(MxtServerConfig.INSTANCE.curios.backMode.getValue(), context, stack));
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "belt_item_auto"), (context, stack) -> acceptsBelt(MxtServerConfig.INSTANCE.curios.beltMode.getValue(), context, stack));
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "charm_artifact_auto"), CuriosIntegration::acceptsCharm);
    }

    // Membership is the definition's answer, not the stack's; Curios' built-in curios:tag validator stays merged.
    private static boolean acceptsCharm(SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Provider access = registries(context);
        return access != null && ArtifactService.curiosEquipable(access, stack);
    }

    private static boolean acceptsBack(BackMode mode, SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Provider access = registries(context);
        return switch (mode) {
            case MANUAL -> false;
            case ALL -> true;
            case WEAPONS -> access != null && ItemBindingService.weapon(access, stack).isPresent();
        };
    }

    private static boolean acceptsBelt(BeltMode mode, SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Provider access = registries(context);
        return switch (mode) {
            case MANUAL -> false;
            case ALL -> true;
            // "Is it an artifact" is asked of the definition rather than of a component being present, so a
            // pack decides which of its items may be worn on the belt instead of the mere existence of state.
            case WEAPONS_ARTIFACTS -> access != null && (ItemBindingService.weapon(access, stack).isPresent()
                    || ArtifactService.curiosEquipable(access, stack));
        };
    }

    // An entity-less context is not Curios' mistake: it asks these predicates while building the recipe book and
    // the creative search index, where nothing can resolve a definition. Reading context.entity() crashed there.
    private static @Nullable Provider registries(SlotContext context) {
        LivingEntity entity = context.entity();
        return entity == null ? null : entity.level().registryAccess();
    }

    public static List<ItemStack> equipped(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(CuriosIntegration::equipped)
                .orElseGet(List::of);
    }

    // The handler's own stacks rather than copies, for a caller that writes to them (an upkeep penalty that
    // wears the artifact, say). A caller that only reads wants {@link #equipped} and no live stack.
    public static List<ItemStack> equippedLive(LivingEntity entity) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity);
        ArrayList<ItemStack> result = new ArrayList<>();
        if (optional.isEmpty()) return result;
        for (ICurioStacksHandler handler : optional.get().getCurios().values()) result.addAll(stacks(handler, false));
        return result;
    }

    private static List<ItemStack> equipped(ICuriosItemHandler inventory) {
        ArrayList<ItemStack> result = new ArrayList<>();
        for (ICurioStacksHandler handler : inventory.getCurios().values()) result.addAll(stacks(handler, true));
        return result;
    }

    // A slot the entity does not have reads as empty rather than failing.
    public static List<ItemStack> equippedIn(LivingEntity entity, String slot) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.getCurios().get(slot))
                .map(CuriosIntegration::stacks)
                .orElseGet(List::of);
    }

    private static List<ItemStack> stacks(ICurioStacksHandler handler) {
        return stacks(handler, true);
    }

    private static List<ItemStack> stacks(ICurioStacksHandler handler, boolean copy) {
        IDynamicStackHandler stacks = handler.getStacks();
        ArrayList<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < stacks.getSlots(); index++) {
            ItemStack stack = stacks.getStackInSlot(index);
            if (!stack.isEmpty()) result.add(copy ? stack.copy() : stack);
        }
        return result;
    }

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
            if (!MxtServerConfig.INSTANCE.curios.forceRenderSlots.getValue() && (!handler.isVisible() || index >= handler.getRenders().size() || !handler.getRenders().get(index)))
                continue;
            ItemStack stack = stacks.getStackInSlot(index);
            if (!stack.isEmpty()) result.put(places[index], stack);
        }
    }

    public enum Place {
        BACK_LEFT, BACK_RIGHT, BELT_LEFT, BELT_RIGHT
    }
}
