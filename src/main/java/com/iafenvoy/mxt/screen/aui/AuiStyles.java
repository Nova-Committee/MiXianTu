package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Colour helpers for the ApricityUI pages. Tooltips are not here: every screen in the mod draws them with the
 * vanilla renderer, so there is no page-side tooltip style to keep in step with the theme.
 */
public final class AuiStyles {
    public static final String INK = "--ink", INK_DARK = "--ink-dark", INK_MUTED = "--ink-muted";
    public static final String CANVAS = "--canvas", SURFACE = "--surface", SURFACE_SOFT = "--surface-soft", EDGE = "--edge";
    public static final String GREEN = "--green", GOLD = "--gold", RED = "--red", PURPLE = "--purple";

    @Nullable
    private static String property(Element element, String name) {
        for (Element current = element; current != null; current = current.parentElement) {
            String value = current.getComputedStyle().getCustomProperty(name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    public static int color(Element element, String name) {
        String value = property(element, name);
        return Color.parse(value == null ? element.getComputedStyle().color : value);
    }

    // Canvas operations may run later; resolve the page palette before submitting the operation.
    public static Palette palette(Element element) {
        return new Palette(color(element, INK), color(element, INK_MUTED), color(element, SURFACE),
                color(element, SURFACE_SOFT), color(element, EDGE), color(element, GREEN),
                color(element, GOLD), color(element, RED), color(element, PURPLE));
    }

    public record Palette(int ink, int muted, int surface, int surfaceSoft, int edge,
                          int green, int gold, int red, int purple) {
    }

    // The warm paper is darker than white; cap each channel to preserve 4.5:1 text contrast.
    public static int readableOnPaper(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int brightest = Math.max(red, Math.max(green, blue));
        if (brightest <= 92) return rgb & 0xFFFFFF;
        double scale = 92.0D / brightest;
        return ((int) Math.round(red * scale) << 16) | ((int) Math.round(green * scale) << 8) | (int) Math.round(blue * scale);
    }

    public static String textColor(int rgb) {
        return hex(AuiTheme.current().dark() ? readableOnDark(rgb) : readableOnPaper(rgb));
    }

    public static String textColor(Element element, int rgb) {
        String background = property(element, "--surface-deep");
        if (background == null) return textColor(rgb);
        int surface = Color.parse(background);
        int candidate = luminance(surface) > 0.5 ? readableOnPaper(rgb) : rgb;
        return hex(readableOnBackground(candidate, surface));
    }

    // Both progress tracks reserve 2px at each end for the inset frame.
    public static int progressWidth(int width, double fraction) {
        if (!Double.isFinite(fraction)) return 0;
        return (int) Math.round((Math.max(4, width) - 4) * Math.clamp(fraction, 0.0D, 1.0D));
    }

    private static final int MIN_BRIGHTNESS = 160;

    /**
     * Lifts a data-driven colour (an aura's or resource's particle tint) until it is readable on the dark skin.
     * Those colours are authored without knowing the background, and a dark one - perfectly fine on a light panel -
     * disappears on this one. The hue is kept: every channel is blended toward white by the same amount.
     */
    public static int readableOnDark(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int brightest = Math.max(red, Math.max(green, blue));
        if (brightest >= MIN_BRIGHTNESS) return readableOnBackground(rgb, 0x293943);
        double amount = (MIN_BRIGHTNESS - brightest) / (255.0D - brightest);
        return readableOnBackground((lift(red, amount) << 16) | (lift(green, amount) << 8) | lift(blue, amount), 0x293943);
    }

    public static int readableOnBackground(int rgb, int background) {
        rgb &= 0xFFFFFF;
        double surface = luminance(background);
        if (contrast(luminance(rgb), surface) >= 4.5) return rgb;
        int target = contrast(1, surface) >= contrast(0, surface) ? 255 : 0;
        double low = 0, high = 1;
        int result = target * 0x010101;
        for (int step = 0; step < 16; step++) {
            double amount = (low + high) / 2;
            int candidate = blend(rgb, target, amount);
            if (contrast(luminance(candidate), surface) >= 4.5) {
                high = amount;
                result = candidate;
            } else low = amount;
        }
        return result;
    }

    private static int blend(int rgb, int target, double amount) {
        int red = (int) Math.round(((rgb >> 16) & 255) * (1 - amount) + target * amount);
        int green = (int) Math.round(((rgb >> 8) & 255) * (1 - amount) + target * amount);
        int blue = (int) Math.round((rgb & 255) * (1 - amount) + target * amount);
        return (red << 16) | (green << 8) | blue;
    }

    private static double contrast(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(int rgb) {
        return channel((rgb >> 16) & 255) * 0.2126 + channel((rgb >> 8) & 255) * 0.7152 + channel(rgb & 255) * 0.0722;
    }

    private static double channel(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static int lift(int channel, double amount) {
        return (int) Math.round(channel + (255 - channel) * amount);
    }

    /**
     * A colour the way the page writes it; the alpha a data-driven colour may carry is dropped.
     */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static void extract(Screen screen, GuiGraphicsExtractor graphics) {
        screen.extractTransparentBackground(graphics);
    }
}
