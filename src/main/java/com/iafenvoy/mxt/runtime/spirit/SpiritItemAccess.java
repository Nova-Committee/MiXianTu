package com.iafenvoy.mxt.runtime.spirit;

import net.minecraft.world.item.ItemStack;

/**
 * An item whose individual stack can exchange whole units of spirit power.
 * Both operations return the part of {@code amount} that could not be moved.
 */
public interface SpiritItemAccess {
    int add(ItemStack stack, int capacity, int amount, boolean simulate);

    int extract(ItemStack stack, int capacity, int amount, boolean simulate);
}
