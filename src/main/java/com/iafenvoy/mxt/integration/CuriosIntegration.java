package com.iafenvoy.mxt.integration;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct Curios integration. Curios is a required, jar-in-jar dependency, so
 * callers can use its stable API without reflective optional-mod handling.
 */
public final class CuriosIntegration {
    private CuriosIntegration() {
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
        List<ItemStack> result = new ArrayList<>();
        for (ICurioStacksHandler handler : inventory.getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int index = 0; index < stacks.getSlots(); index++) {
                ItemStack stack = stacks.getStackInSlot(index);
                if (!stack.isEmpty()) result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }
}
