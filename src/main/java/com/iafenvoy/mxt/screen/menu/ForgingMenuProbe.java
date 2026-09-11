package com.iafenvoy.mxt.screen.menu;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Exposes the step rows' window arithmetic to the server audit in {@code mxt_test}.
 *
 * <p>Mirrors {@code AuraZonePriorityProbe} and {@code ForgingProbe}: the audit exercises the encoder the
 * screen actually uses rather than a copy of it. This one earns its place because the rows are a place
 * where being wrong is invisible. A row computed from the wrong end of the finish pattern fills exactly
 * the same six cells with plausible icons, so nothing about the picture says which end it came from -
 * only the server's refusal does, several strikes later.</p>
 *
 * <p>The menu is common code, so this loads on a dedicated server; it is the screen that does not.</p>
 */
public final class ForgingMenuProbe {
    private ForgingMenuProbe() {
    }

    /**
     * The target row: the registry id each of the six cells will be drawn from, or {@code ForgingMenu.NONE}.
     */
    public static int[] targetRow(List<Identifier> pattern, int required) {
        return row(position -> ForgingMenu.requiredStep(pattern, position, required));
    }

    /**
     * The current row, the same way.
     */
    public static int[] historyRow(List<Identifier> history) {
        return row(position -> ForgingMenu.historyStep(history, position));
    }

    private static int[] row(IntUnaryOperator position) {
        int[] cells = new int[ForgingMenu.SUFFIX_STEPS];
        for (int index = 0; index < cells.length; index++) cells[index] = position.applyAsInt(index);
        return cells;
    }
}
