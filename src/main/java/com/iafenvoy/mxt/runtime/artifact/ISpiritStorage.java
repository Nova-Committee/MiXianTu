package com.iafenvoy.mxt.runtime.artifact;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Common storage boundary; the default implementation is {@link ArtifactStorageService#INSTANCE}.
 */
public interface ISpiritStorage {
    int slots(ItemStack stack);

    ItemStack get(ItemStack stack, int slot, Player viewer);

    boolean set(ItemStack stack, int slot, ItemStack value, Player viewer);
}
