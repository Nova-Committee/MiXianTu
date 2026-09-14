package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;

import java.util.Collection;

public final class InformationHelper {
    /**
     * Space kept between the label column and the value column.
     */
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
     * Splits the width available to a row into a label column and a value column.
     *
     * <p>The value is data while the label is a name that the row can abbreviate and show in full in a
     * tooltip, so a value that needs room narrows the label instead of being cut off. The label keeps at
     * least a quarter of the row so the row stays identifiable, and {@code preferredNameWidth} still wins
     * whenever nothing has to give, which is what keeps the values aligned in one column.</p>
     *
     * <p>This is pure arithmetic rather than a {@code Font} call so it can be audited without a client.</p>
     */
    public static Columns columns(int availableWidth, int preferredNameWidth, int valueWidth) {
        int width = Math.max(1, availableWidth);
        int wanted = Math.max(0, Math.min(valueWidth, width));
        int roomForName = Math.max(width / 4, width - wanted - COLUMN_GAP);
        int name = Math.max(0, Math.min(preferredNameWidth, roomForName));
        return new Columns(name, Math.max(1, width - name - COLUMN_GAP));
    }
}
