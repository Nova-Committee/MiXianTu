package com.iafenvoy.mxt.util;

import java.util.Locale;

/**
 * Common formatting helpers for item tooltip values.
 */
public final class TooltipText {
    private TooltipText() {
    }

    public static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.00$", "").replaceAll("(\\.\\d)0$", "$1");
    }

    public static String signed(double value) {
        return (value >= 0.0D ? "+" : "") + number(value);
    }
}
