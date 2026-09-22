package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Common storage boundary; the default implementation is {@link ArtifactStorageService#INSTANCE}. Every call takes
 * the caller's registries, because the slot count is declared by a datapack definition and has to be readable on
 * the client too.
 */
public interface ISpiritStorage {
    int slots(Provider access, ItemStack stack, FormulaContext context);

    ItemStack get(Provider access, ItemStack stack, int slot, Player viewer);

    boolean set(Provider access, ItemStack stack, int slot, ItemStack value, Player viewer);
}
