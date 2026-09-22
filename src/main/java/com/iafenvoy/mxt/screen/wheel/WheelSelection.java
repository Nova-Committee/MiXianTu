package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;

/**
 * What was used, and how. The last two are fed by the same use key and differ only in how it was asked for.
 *
 * @param source which source the cell read from, which is what the server re-reads before honouring it
 * @param number the cell it sat in, as the wheel numbers them
 * @param entry  the entry that cell held, never null
 * @param method how the use was asked for
 */
public record WheelSelection(WheelSource source, int number, WheelMenuEntry entry, Method method) {
    public enum Method {
        /** While the wheel is open the pointed cell, while it is closed the remembered one. */
        KEY,
        /** The left mouse button was clicked while the wheel was open. */
        CLICK
    }
}
