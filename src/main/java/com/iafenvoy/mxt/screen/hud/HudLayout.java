package com.iafenvoy.mxt.screen.hud;

import com.iafenvoy.mxt.config.MxtHudConfig;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * Where a HUD entry sits, as the client config keeps it.
 *
 * <p>This is the port of the storage half of AxolotlClient's HUD framework ("This implementation of Hud
 * modules is based on KronHUD", GPL-3.0), reduced to what this mod needs. KronHUD stores a position as a
 * pair of ratios in {@code 0..1}, not as pixels: the ratio survives a window resize, a change of GUI scale
 * and a change of GUI scale mid-session, none of which the framework can be told about. Scaling is left
 * out of this port, so a ratio is not carrying an additional scale factor here - it is only the position
 * divided by the window, which is what makes a stored layout portable between two clients with different
 * resolutions.</p>
 *
 * <p>The value under each key is {@code x,y,visible}; an entry that is not mentioned has never been placed,
 * so it keeps asking for the position its own class suggests. Values are written back through
 * {@link MxtHudConfig} and land in {@code config/mxt/mxt-hud.json}, which means a hand-edited file is read like
 * any other config value and a malformed one falls back to the entry's own default rather than throwing
 * during startup.</p>
 *
 * <p>Ratios are only half the answer: whatever comes out is still clamped into the window by
 * {@link AbstractHudEntry}, so an entry can never be dragged (or stored) entirely off screen.</p>
 */
public final class HudLayout {
    private static final char SEPARATOR = ',';
    private static final int DECIMALS = 4;

    private HudLayout() {
    }

    /**
     * The stored placement of one entry, as a ratio of the window plus its visibility.
     */
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

    /**
     * Stores one placement, which also writes the config file. That immediate write is deliberate: a drag
     * has no other moment at which it could be saved, because the edit screen writes as the mouse moves and
     * the framework holds no dirty flag that a later close would flush.
     */
    public static void write(String layoutKey, Placement placement) {
        MxtHudConfig.INSTANCE.hud.store(layoutKey, format(placement));
    }

    /**
     * Forgets one placement and writes the config file, so the entry goes back to the position its class
     * suggests - on this launch and on the next one. Called by a reset, and the only way a layout key leaves
     * the file.
     */
    public static void clear(String layoutKey) {
        MxtHudConfig.INSTANCE.hud.remove(layoutKey);
    }

    static String format(Placement placement) {
        return round(placement.xRatio()) + "," + round(placement.yRatio()) + "," + placement.visible();
    }

    /**
     * The scaled window size in pixels, or {@code null} while there is no window - which is the case during
     * the earliest part of client startup. An entry that computes its own default position asks this first
     * and answers something harmless if there is no window yet; the default is re-applied on the next frame,
     * so a value chosen before the window existed is never the one that sticks.
     */
    public static int[] window() {
        return windowSize();
    }

    /**
     * The scaled window in pixels, or {@code null} while no window exists - which is the case during the
     * earliest part of client startup. Callers treat that as "cannot place anything yet" instead of
     * guessing a size.
     */
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
