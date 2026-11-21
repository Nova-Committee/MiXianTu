package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.screen.AuraDisplay;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.saturn.util.formula.number.Expression;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small, bounded text helpers shared by the JEI recipe categories.
 */
public final class SpiritJeiText {
    public static final int PANEL_BACKGROUND = 0xC6C6C6;
    private static final double TEXT_CONTRAST_TARGET = 4.5D;

    private SpiritJeiText() {
    }

    public static List<AuraEntry> auraEntries(List<Cost> costs) {
        List<AuraEntry> entries = new ArrayList<>();
        for (Cost cost : costs)
            if (cost instanceof AuraCost(
                    net.minecraft.core.Holder<com.iafenvoy.mxt.data.aura.Aura> aura, NumberProvider amount
            ))
                entries.add(new AuraEntry(AuraDisplay.of(aura), providerName(amount)));
        return List.copyOf(entries);
    }

    public record AuraEntry(AuraDisplay display, String amount) {
        public List<Component> tooltip() {
            return this.display.tooltip(Component.literal("× " + this.amount));
        }
    }

    public static String providerName(NumberProvider provider) {
        if (provider instanceof Constant(double value)) {
            if (value == Math.rint(value)) return Long.toString((long) value);
            return String.format(Locale.ROOT, "%.2f", value);
        }
        if (provider instanceof Expression expression) return expression.source();
        return Component.translatable("jei.mxt.aura_dynamic").getString();
    }

    static String trim(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String clipped = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("...")));
        return clipped + (font.width("...") <= maxWidth ? "..." : "");
    }

    public static int readableTextColor(int candidate, int background) {
        return contrastRatio(background, candidate) >= TEXT_CONTRAST_TARGET
                ? candidate : (contrastRatio(background, 0x000000) >= contrastRatio(background, 0xFFFFFF) ? 0x000000 : 0xFFFFFF);
    }

    private static double contrastRatio(int first, int second) {
        double a = relativeLuminance(first);
        double b = relativeLuminance(second);
        double light = Math.max(a, b);
        double dark = Math.min(a, b);
        return (light + 0.05D) / (dark + 0.05D);
    }

    private static double relativeLuminance(int color) {
        double red = linear(((color >>> 16) & 0xFF) / 255.0D);
        double green = linear(((color >>> 8) & 0xFF) / 255.0D);
        double blue = linear((color & 0xFF) / 255.0D);
        return 0.2126D * red + 0.7152D * green + 0.0722D * blue;
    }

    private static double linear(double channel) {
        return channel <= 0.03928D ? channel / 12.92D : Math.pow((channel + 0.055D) / 1.055D, 2.4D);
    }
}
