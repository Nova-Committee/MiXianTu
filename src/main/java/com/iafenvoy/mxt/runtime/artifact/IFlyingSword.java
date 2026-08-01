package com.iafenvoy.mxt.runtime.artifact;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Content-facing flying-sword hook. Movement and energy remain in FlightService.
 */
public interface IFlyingSword {
    boolean canMount(ItemStack stack, Player player);

    default void onMount(ItemStack stack, Player player) {
    }

    default void onDismount(ItemStack stack, Player player) {
    }

    default void onAttack(ItemStack stack, Player player) {
    }
}
