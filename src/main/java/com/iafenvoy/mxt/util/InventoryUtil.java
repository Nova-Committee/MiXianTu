package com.iafenvoy.mxt.util;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Shared exact-item inventory operations for station transactions.
 */
public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static Container copy(Container source) {
        SimpleContainer copy = new SimpleContainer(source.getContainerSize());
        for (int index = 0; index < source.getContainerSize(); index++)
            copy.setItem(index, source.getItem(index).copy());
        return copy;
    }

    public static boolean hasAllItems(Container target, Container requirements) {
        return removeItems(copy(target), requirements);
    }

    public static boolean removeItems(Container target, Container requirements) {
        boolean changed = false;
        for (int requirementIndex = 0; requirementIndex < requirements.getContainerSize(); requirementIndex++) {
            ItemStack requirement = requirements.getItem(requirementIndex);
            int remaining = requirement.getCount();
            if (requirement.isEmpty()) continue;
            for (int targetIndex = 0; targetIndex < target.getContainerSize() && remaining > 0; targetIndex++) {
                ItemStack candidate = target.getItem(targetIndex);
                if (!ItemStack.isSameItemSameComponents(candidate, requirement)) continue;
                int removed = Math.min(remaining, candidate.getCount());
                candidate.shrink(removed);
                remaining -= removed;
                changed = true;
            }
            if (remaining > 0) {
                if (changed) target.setChanged();
                return false;
            }
        }
        if (changed) target.setChanged();
        return true;
    }

    public static boolean insertItems(Container target, Container additions) {
        boolean changed = false;
        for (int index = 0; index < additions.getContainerSize(); index++) {
            ItemStack remainder = additions.getItem(index).copy();
            if (remainder.isEmpty()) continue;
            for (int targetIndex = 0; targetIndex < target.getContainerSize() && !remainder.isEmpty(); targetIndex++) {
                ItemStack candidate = target.getItem(targetIndex);
                if (!ItemStack.isSameItemSameComponents(candidate, remainder)) continue;
                int inserted = Math.min(remainder.getCount(), candidate.getMaxStackSize() - candidate.getCount());
                if (inserted > 0) {
                    candidate.grow(inserted);
                    remainder.shrink(inserted);
                    changed = true;
                }
            }
            for (int targetIndex = 0; targetIndex < target.getContainerSize() && !remainder.isEmpty(); targetIndex++) {
                if (!target.getItem(targetIndex).isEmpty()) continue;
                int inserted = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                target.setItem(targetIndex, remainder.copyWithCount(inserted));
                remainder.shrink(inserted);
                changed = true;
            }
            if (!remainder.isEmpty()) {
                if (changed) target.setChanged();
                return false;
            }
        }
        if (changed) target.setChanged();
        return true;
    }
}
