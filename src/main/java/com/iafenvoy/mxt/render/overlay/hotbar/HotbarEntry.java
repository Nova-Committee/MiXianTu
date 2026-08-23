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

    default void onPress(Player player) {
    }

    default void onPressTick(Player player) {
    }

    default void onRelease(Player player) {
    }
}
