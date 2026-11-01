package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.saturn.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;

import java.util.Collection;

public final class InformationHelper {
    private static final int COLUMN_GAP = 8;

    private InformationHelper() {
    }

    public static void lineWithDefinitions(InformationCollector collector, String key, Collection<? extends Holder<?>> values) {
        if (!values.isEmpty()) collector.add(key, joinDefinitions(values));
    }

    public static String joinDefinitions(Collection<? extends Holder<?>> values) {
        return values.stream().map(holder -> DefinitionText.nameOrId(holder).getString()).reduce((a, b) -> a + ", " + b).orElse("-");
    }

    public record Columns(int nameWidth, int valueWidth) {
    }

    // The value is data and the label can be abbreviated and tooltipped, so a long value narrows the label
    // instead of being cut off. Call it once per block with the block's widest name and widest value: rows that
    // measure themselves line up only while every row has a name of the same width.
    public static Columns columns(int availableWidth, int widestNameWidth, int widestValueWidth) {
        int width = Math.max(1, availableWidth);
        int wanted = Mth.clamp(width, 0, widestValueWidth);
        int roomForName = Math.max(width / 4, width - wanted - COLUMN_GAP);
        int name = Mth.clamp(roomForName, 0, widestNameWidth);
        return new Columns(name, Math.max(1, width - name - COLUMN_GAP));
    }
}
