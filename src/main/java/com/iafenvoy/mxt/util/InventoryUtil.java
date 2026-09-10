package com.iafenvoy.mxt.util;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Shared exact-item inventory operations for station transactions. Both mutation helpers are
 * atomic: they either apply in full or leave the target container untouched.
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

    /**
     * Removes every requirement from the target. The call is atomic: when a requirement cannot be
     * satisfied, every change made so far is rolled back and the target is left untouched.
     */
    public static boolean removeItems(Container target, Container requirements) {
        int size = target.getContainerSize();
        ItemStack[] snapshot = new ItemStack[size];
        for (int index = 0; index < size; index++) snapshot[index] = target.getItem(index).copy();
        if (remove(target, requirements, size)) return true;
        restore(target, snapshot);
        return false;
    }

    private static boolean remove(Container target, Container requirements, int size) {
        boolean changed = false;
        for (int requirementIndex = 0; requirementIndex < requirements.getContainerSize(); requirementIndex++) {
            ItemStack requirement = requirements.getItem(requirementIndex);
            int remaining = requirement.getCount();
            if (requirement.isEmpty()) continue;
            for (int targetIndex = 0; targetIndex < size && remaining > 0; targetIndex++) {
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

    /**
     * Inserts every addition into the target. The call is atomic: when one addition cannot be
     * placed, the target is restored to its previous contents before returning {@code false}.
     */
    public static boolean insertItems(Container target, Container additions) {
        int size = target.getContainerSize();
        ItemStack[] snapshot = new ItemStack[size];
        for (int index = 0; index < size; index++) snapshot[index] = target.getItem(index).copy();
        if (insert(target, additions, size)) return true;
        restore(target, snapshot);
        return false;
    }

    private static void restore(Container target, ItemStack[] snapshot) {
        for (int index = 0; index < snapshot.length; index++) target.setItem(index, snapshot[index].copy());
        target.setChanged();
    }

    private static boolean insert(Container target, Container additions, int size) {
        boolean changed = false;
        for (int index = 0; index < additions.getContainerSize(); index++) {
            ItemStack remainder = additions.getItem(index).copy();
            if (remainder.isEmpty()) continue;
            for (int targetIndex = 0; targetIndex < size && !remainder.isEmpty(); targetIndex++) {
                ItemStack candidate = target.getItem(targetIndex);
                if (!ItemStack.isSameItemSameComponents(candidate, remainder)) continue;
                int inserted = Math.min(remainder.getCount(), candidate.getMaxStackSize() - candidate.getCount());
                if (inserted > 0) {
                    candidate.grow(inserted);
                    remainder.shrink(inserted);
                    changed = true;
                }
            }
            for (int targetIndex = 0; targetIndex < size && !remainder.isEmpty(); targetIndex++) {
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
