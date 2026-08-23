package com.iafenvoy.mxt.render.overlay.hotbar;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * One selectable entry rendered by the shared client hotbar.
 */
public interface HotbarEntry {
    Component name();

    default Optional<ItemStack> icon() {
        return Optional.empty();
    }

    default int accentColor() {
        return 0xFF7E8799;
    }

    /**
     * Returns the remaining cooldown fraction, matching vanilla item cooldown
     * rendering semantics. A value of {@code 0} means ready and {@code 1}
     * means the cooldown has just started.
     */
    default float cooldown(Player player) {
        return 0.0F;
    }

    default void onPress(Player player) {
    }

    default void onPressTick(Player player) {
    }

    default void onRelease(Player player) {
    }
}
