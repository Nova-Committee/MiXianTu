package com.iafenvoy.mxt.screen.hud;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

import java.util.Optional;

/**
 * The one place an element's stored placement and the window it lives in are read. Nothing here knows about
 * elements: a layout key goes in, pixels come out, and what comes out is still clamped into the window by
 * {@link AbstractHudEntry}, so an element cannot end up somewhere the player cannot see it.
 */
public final class HudLayout {
    private HudLayout() {
    }

    public static Optional<HudPlacement> read(String layoutKey) {
        return HudLayoutFile.get(layoutKey);
    }

    // Written immediately: the edit screen stores as the mouse moves, so a drag has no other moment at which it
    // could be saved and there is no dirty flag a later close would flush.
    public static void write(String layoutKey, HudPlacement placement) {
        HudLayoutFile.put(layoutKey, placement);
    }

    // The only way a layout key leaves the file; the element goes back to the placement its class suggests.
    public static void clear(String layoutKey) {
        HudLayoutFile.remove(layoutKey);
    }

    public static int[] window() {
        return windowSize();
    }

    // null while there is no window yet - the earliest part of client startup - which callers treat as
    // "cannot place anything yet" rather than guessing a size.
    static int[] windowSize() {
        Window window = Minecraft.getInstance().getWindow();
        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        return width <= 0 || height <= 0 ? null : new int[]{width, height};
    }
}
