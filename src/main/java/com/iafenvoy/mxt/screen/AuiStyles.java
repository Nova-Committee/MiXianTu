package com.iafenvoy.mxt.screen;

import com.sighs.apricityui.ui.Tooltip;

/**
 * The one place the ApricityUI pages' shared look is written from Java.
 * <p>
 * A page's tooltip arrives with ApricityUI's own inline style (an 11px web font, 15px line height); the style
 * below is appended after it inside the same inline block, so these declarations win without depending on the
 * stylesheet cascade (a stylesheet {@code !important} rule is the other way round and did not stick). The font
 * size has to stay a multiple of 9px: that backend scales the vanilla bitmap font rather than setting pixels.
 */
public final class AuiStyles {
    public static final Tooltip.Options TOOLTIP = new Tooltip.Options(
            null,
            "font-family:initial;font-size:9px;line-height:9px;font-weight:400;min-width:0;"
                    + "padding:4px 6px;border:1px solid rgba(255,255,255,0.18);"
                    + "background-color:rgba(0,0,0,0.86);box-shadow:2px 2px 0 rgba(0,0,0,0.35);",
            12, 16, 200);

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
