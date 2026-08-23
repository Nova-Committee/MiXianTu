package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.Expression;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Small, bounded text helpers used by both spirit recipe categories.
 */
final class SpiritJeiText {
    private static final int PANEL_BACKGROUND = 0xC6C6C6;
    private static final double TEXT_CONTRAST_TARGET = 4.5D;

    private SpiritJeiText() {
    }

    static List<Component> auraLines(Map<Holder<Resource>, NumberProvider> aura, Font font, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        if (aura.isEmpty()) {
            lines.add(Component.translatable("jei.mxt.aura_cost_none"));
            return lines;
        }
        boolean first = true;
        for (Entry<Holder<Resource>, NumberProvider> entry : aura.entrySet()) {
            lines.add(auraLine(entry, first, font, maxWidth));
            first = false;
            if (lines.size() == 3) break;
        }
        return lines;
    }

    private static MutableComponent auraLine(Entry<Holder<Resource>, NumberProvider> entry, boolean first, Font font, int maxWidth) {
        String value = providerName(entry.getValue());
        String name = resourceName(entry.getKey());
        MutableComponent line = first ? Component.translatable("jei.mxt.aura_cost_label") : Component.empty();
        int color = entry.getKey().value().auraType().map(type -> type.value().color()).orElse(0xFFFFFF);
        line.append(Component.literal(name).withColor(readableTextColor(color, PANEL_BACKGROUND)));
        line.append(Component.literal(" x" + value));
        if (font.width(line) <= maxWidth) return line;
        String shortened = trim(font, value, Math.max(12, maxWidth - font.width(name) - (first ? 22 : 8)));
        line = first ? Component.translatable("jei.mxt.aura_cost_label") : Component.empty();
        line.append(Component.literal(name).withColor(readableTextColor(color, PANEL_BACKGROUND)));
        return line.append(Component.literal(" x" + shortened));
    }

    private static String resourceName(Holder<Resource> holder) {
        return HolderHelper.id(holder).getPath();
    }

    private static String providerName(NumberProvider provider) {
        if (provider instanceof Constant(double value)) {
            if (value == Math.rint(value)) return Long.toString((long) value);
            return String.format(Locale.ROOT, "%.2f", value);
        }
        if (provider instanceof Expression expression) return expression.source();
        return Component.translatable("jei.mxt.aura_dynamic").getString();
    }

    private static String trim(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String clipped = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("...")));
        return clipped + "...";
    }

    private static int readableTextColor(int candidate, int background) {
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
