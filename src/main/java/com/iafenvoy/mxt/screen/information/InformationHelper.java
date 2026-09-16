package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;

import java.util.Collection;

public final class InformationHelper {
    private static final int COLUMN_GAP = 8;

    private InformationHelper() {
    }

    public static void lineWithDefinitions(InformationCollector collector, String key, Collection<? extends Holder<?>> values, String category) {
        if (!values.isEmpty()) collector.add(key, joinDefinitions(values, category));
    }

    public static String joinDefinitions(Collection<? extends Holder<?>> values, String category) {
        return values.stream().map(holder -> DefinitionText.name(holder, category).getString()).reduce((a, b) -> a + ", " + b).orElse("-");
    }

    /**
     * How wide an information row's two columns are.
     *
     * @param nameWidth  the label column, never wider than {@code preferredNameWidth}
     * @param valueWidth the space left for the value
     */
    public record Columns(int nameWidth, int valueWidth) {
    }

    /**
     * Splits the width available to a row into a label column and a value column. The value is data and the
     * label can be abbreviated and tooltipped, so a long value narrows the label instead of being cut off;
     * {@code preferredNameWidth} wins whenever nothing has to give, which keeps the values aligned.
     */
    public static Columns columns(int availableWidth, int preferredNameWidth, int valueWidth) {
        int width = Math.max(1, availableWidth);
        int wanted = Math.max(0, Math.min(valueWidth, width));
        int roomForName = Math.max(width / 4, width - wanted - COLUMN_GAP);
        int name = Math.max(0, Math.min(preferredNameWidth, roomForName));
        return new Columns(name, Math.max(1, width - name - COLUMN_GAP));
    }
}
