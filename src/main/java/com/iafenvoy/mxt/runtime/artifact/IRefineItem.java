package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.runtime.artifact.ArtifactService.RefineResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Optional item integration boundary for ownership and refining.
 */
public interface IRefineItem {
    boolean canRefine(ItemStack stack, Player player);

    RefineResult refine(ItemStack stack, Player player);

    default boolean canRelease(ItemStack stack, Player player) {
        return true;
    }
}
