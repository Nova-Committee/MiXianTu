package com.iafenvoy.mxt.screen.wheel;

/**
 * What was used, and how. The last two are fed by the same use key and differ only in how it was asked for.
 *
 * @param slot   the sector the entry sits in
 * @param entry  the entry registered in that sector, never null
 * @param method how the use was asked for
 */
public record WheelSelection(int slot, WheelMenuEntry entry, Method method) {
    public enum Method {
        /** While the wheel is open the pointed sector, while it is closed the remembered one. */
        KEY,
        /** The left mouse button was clicked while the wheel was open. */
        CLICK
    }
}
