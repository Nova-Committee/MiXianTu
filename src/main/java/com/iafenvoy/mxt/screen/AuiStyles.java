package com.iafenvoy.mxt.screen;

/**
 * Colour helpers for the ApricityUI pages. Tooltips are not here: every screen in the mod draws them with the
 * vanilla renderer, so there is no page-side tooltip style to keep in step with the theme.
 */
public final class AuiStyles {
    /**
     * The brightest channel a colour needs before the dark page skin can carry it.
     */
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
        if (brightest >= MIN_BRIGHTNESS) return rgb & 0xFFFFFF;
        double amount = (MIN_BRIGHTNESS - brightest) / (255.0D - brightest);
        return (lift(red, amount) << 16) | (lift(green, amount) << 8) | lift(blue, amount);
    }

    private static int lift(int channel, double amount) {
        return (int) Math.round(channel + (255 - channel) * amount);
    }

    private AuiStyles() {
    }
}
