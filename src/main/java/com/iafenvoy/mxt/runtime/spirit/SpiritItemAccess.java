package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.cultivation.Element;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

/**
 * An item whose individual stack can exchange whole units of spirit power.
 * Both operations return the part of {@code amount} that could not be moved.
 */
public interface SpiritItemAccess {
    /**
     * Returns this stack's current data-driven spirit capacity.
     */
    int getCapacity(ItemStack stack);

    /**
     * Attempts to add one elemental aura type to this stack.
     */
    int add(ItemStack stack, Holder<Element> type, int amount, boolean simulate);

    /**
     * Attempts to extract one elemental aura type from this stack.
     */
    int extract(ItemStack stack, Holder<Element> type, int amount, boolean simulate);
}
