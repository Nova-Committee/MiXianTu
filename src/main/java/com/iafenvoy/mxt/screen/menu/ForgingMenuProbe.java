package com.iafenvoy.mxt.screen.menu;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Exposes the step rows' window arithmetic to the server audit in {@code mxt_test}, mirroring
 * {@code ForgingProbe}: the audit exercises the encoder the screen actually uses rather than a copy.
 * This one earns its place because a row computed from the wrong end of the finish pattern fills
 * exactly the same cells with plausible icons, so only the server's later refusal reveals it.
 */
public final class ForgingMenuProbe {
    private ForgingMenuProbe() {
    }

    /**
     * The registry id each of the six cells of the target row will be drawn from, or
     * {@code ForgingMenu.NONE}.
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
