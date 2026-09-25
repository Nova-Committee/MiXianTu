package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.api.WheelSource;

/**
 * What was used, and how: {@code source} is what the server re-reads before honouring the cell, and the two
 * methods are fed by the same use key, differing only in how it was asked for.
 */
public record WheelSelection(WheelSource source, int number, WheelMenuEntry entry, Method method) {
    public enum Method {
        // While the wheel is open the pointed cell, while it is closed the remembered one.
        KEY,
        // The left mouse button was clicked while the wheel was open.
        CLICK
    }
}