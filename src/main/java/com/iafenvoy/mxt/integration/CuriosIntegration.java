package com.iafenvoy.mxt.integration;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.resources.Identifier;

/**
 * Direct Curios integration. Curios is a required, jar-in-jar dependency, so
 * callers can use its stable API without reflective optional-mod handling.
 */
public final class CuriosIntegration {
    private static final Map<Item, BackHolder> BACK_HOLDERS = new HashMap<>();
    private static final Map<Item, BeltHolder> BELT_HOLDERS = new HashMap<>();

    private CuriosIntegration() {
    }

    /**
     * Registers the data-driven automatic acceptance predicates used by the back and belt slots.
     */
    public static void registerPredicates() {
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "back_auto"),
                (context, stack) -> acceptsBack(MxtServerConfig.backMode(), context, stack));
        CuriosSlotTypes.registerPredicate(Identifier.fromNamespaceAndPath("mxt", "belt_auto"),
                (context, stack) -> acceptsBelt(MxtServerConfig.beltMode(), context, stack));
    }

    private static boolean acceptsBack(MxtServerConfig.BackMode mode, SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (mode) {
            case MANUAL -> false;
            case ALL -> true;
            case WEAPONS -> ItemBindingService.weapon(context.entity().level().registryAccess(), stack).isPresent();
        };
    }

    private static boolean acceptsBelt(MxtServerConfig.BeltMode mode, SlotContext context, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (mode) {
            case MANUAL -> false;
            case ALL -> true;
            case WEAPONS_ARTIFACTS -> ItemBindingService.weapon(context.entity().level().registryAccess(), stack).isPresent()
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
        java.util.ArrayList<ItemStack> result = new java.util.ArrayList<>();
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
     * Returns the cosmetic stacks exposed by Curios' renderable slots. The
     * back and belt slots are mapped to left/right positions in the same way
     * as the physical slots, so render layers do not need to know Curios' API.
     */
    public static Map<Place, ItemStack> equippedForCosmetic(LivingEntity entity) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity);
        Map<Place, ItemStack> result = new HashMap<>();
        if (optional.isEmpty()) return result;
        ICuriosItemHandler handler = optional.get();
        Map<String, ICurioStacksHandler> all = handler.getCurios();
        findAndApply(all.get("back"), stack -> result.put(Place.BACK_RIGHT, stack), stack -> result.put(Place.BACK_LEFT, stack));
        findAndApply(all.get("belt"), stack -> result.put(Place.BELT_RIGHT, stack), stack -> result.put(Place.BELT_LEFT, stack));
        return result;
    }

    @SafeVarargs
    private static void findAndApply(ICurioStacksHandler handler, Consumer<ItemStack>... consumers) {
        if (handler == null) return;
        for (int index = 0; index < handler.getSlots() && index < consumers.length; index++) {
            if (!handler.getRenders().get(index)) continue;
            ItemStack stack = handler.getCosmeticStacks().getStackInSlot(index);
            if (!stack.isEmpty()) consumers[index].accept(stack);
        }
    }

    public static BackHolder getBack(Item item) {
        return BACK_HOLDERS.get(item);
    }

    public static BeltHolder getBelt(Item item) {
        return BELT_HOLDERS.get(item);
    }

    public static void registerBack(Item item, boolean alone, BiConsumer<PoseStack, Boolean> transformer) {
        BACK_HOLDERS.put(item, new BackHolder(item, alone, transformer));
    }

    public static void registerBack(Item item, BiConsumer<PoseStack, Boolean> transformer) {
        registerBack(item, false, transformer);
    }

    public static void registerBack(boolean alone, BiConsumer<PoseStack, Boolean> transformer, Item... items) {
        for (Item item : items) registerBack(item, alone, transformer);
    }

    public static void registerBack(BiConsumer<PoseStack, Boolean> transformer, Item... items) {
        for (Item item : items) registerBack(item, transformer);
    }

    public static void registerBelt(Item item, BiConsumer<PoseStack, Boolean> transformer) {
        BELT_HOLDERS.put(item, new BeltHolder(item, transformer));
    }

    public static void registerBelt(BiConsumer<PoseStack, Boolean> transformer, Item... items) {
        for (Item item : items) registerBelt(item, transformer);
    }

    public record BackHolder(Item item, boolean alone, BiConsumer<PoseStack, Boolean> transformer) {
    }

    public record BeltHolder(Item item, BiConsumer<PoseStack, Boolean> transformer) {
    }

    public enum Place {
        BACK_LEFT(EquipmentSlot.CHEST),
        BACK_RIGHT(EquipmentSlot.CHEST),
        BELT_LEFT(EquipmentSlot.CHEST),
        BELT_RIGHT(EquipmentSlot.CHEST);

        private final EquipmentSlot slot;

        Place(EquipmentSlot slot) {
            this.slot = slot;
        }

        public EquipmentSlot slot() {
            return this.slot;
        }
    }
}
