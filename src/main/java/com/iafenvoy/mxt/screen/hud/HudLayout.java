package com.iafenvoy.mxt.screen.hud;

import com.iafenvoy.mxt.config.MxtHudConfig;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * Where a HUD entry sits, as the client config keeps it: one {@code x,y,visible} ratio of the window per
 * layout key. A ratio survives a window resize and a GUI scale change; a missing key means the entry was
 * never placed. Whatever comes out is still clamped into the window by {@link AbstractHudEntry}.
 */
public final class HudLayout {
    private static final char SEPARATOR = ',';
    private static final int DECIMALS = 4;

    private HudLayout() {
    }

    public record Placement(double xRatio, double yRatio, boolean visible) {
    }

    public static Optional<Placement> read(String layoutKey) {
        String raw = MxtHudConfig.INSTANCE.hud.stored(layoutKey);
        if (raw == null || raw.isBlank()) return Optional.empty();
        String[] parts = raw.split(String.valueOf(SEPARATOR), -1);
        if (parts.length < 3) return Optional.empty();
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(x) || !Double.isFinite(y)) return Optional.empty();
            return Optional.of(new Placement(x, y, Boolean.parseBoolean(parts[2].trim())));
        } catch (NumberFormatException ignored) {
            // A hand-edited file that no longer parses is treated as "this entry was never placed".
            return Optional.empty();
        }
    }

    // Writes the config file immediately: the edit screen writes as the mouse moves, so a drag has no other
    // moment at which it could be saved and there is no dirty flag a later close would flush.
    public static void write(String layoutKey, Placement placement) {
        MxtHudConfig.INSTANCE.hud.store(layoutKey, format(placement));
    }

    // The only way a layout key leaves the file; the entry goes back to the position its class suggests.
    public static void clear(String layoutKey) {
        MxtHudConfig.INSTANCE.hud.remove(layoutKey);
    }

    static String format(Placement placement) {
        return round(placement.xRatio()) + "," + round(placement.yRatio()) + "," + placement.visible();
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

    private static double round(double value) {
        return Math.round(Mth.clamp(value, 0.0D, 1.0D) * Math.pow(10, DECIMALS)) / Math.pow(10, DECIMALS);
    }
}
