package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Common storage boundary; the default implementation is {@link ArtifactStorageService#INSTANCE}. Every call takes
 * the caller's registries and the ability that declares the container, because the slot count comes from that
 * definition and has to be readable on the client too.
 */
public interface ISpiritStorage {
    int slots(Provider access, ItemStack stack, Holder<Ability> ability, FormulaContext context);

    ItemStack get(Provider access, ItemStack stack, Holder<Ability> ability, int slot, Player viewer);

    boolean set(Provider access, ItemStack stack, Holder<Ability> ability, int slot, ItemStack value, Player viewer);
}
