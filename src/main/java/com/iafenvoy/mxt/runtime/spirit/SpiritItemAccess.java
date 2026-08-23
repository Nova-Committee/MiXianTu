package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.resource.Resource;
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
     * Attempts to add one resource to this stack.
     */
    int add(ItemStack stack, Holder<Resource> resource, int amount, boolean simulate);

    /**
     * Attempts to extract one resource from this stack.
     */
    int extract(ItemStack stack, Holder<Resource> resource, int amount, boolean simulate);
}
