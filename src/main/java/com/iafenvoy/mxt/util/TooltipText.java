package com.iafenvoy.mxt.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Common formatting helpers for item tooltip values.
 */
public final class TooltipText {
    /**
     * The punctuation between two values on one tooltip line. A translation key rather than a literal, because
     * list punctuation differs per language; every joined line goes through {@link #join(List)} so there is one
     * answer to how it is spelled.
     */
    public static final String SEPARATOR = "tooltip.mxt.separator";

    private TooltipText() {
    }

    public static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.00$", "").replaceAll("(\\.\\d)0$", "$1");
    }

    public static String signed(double value) {
        return (value >= 0.0D ? "+" : "") + number(value);
    }

    /**
     * One tooltip line out of its parts. An empty list gives an empty line.
     */
    public static MutableComponent join(List<? extends Component> parts) {
        MutableComponent joined = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) joined.append(Component.translatable(SEPARATOR));
            joined.append(parts.get(index));
        }
        return joined;
    }

    public static MutableComponent join(Component... parts) {
        return join(List.of(parts));
    }
}
