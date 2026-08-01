package com.iafenvoy.mxt.network.payload;

/**
 * Every supported state transition of one server-owned forging session.
 */
public enum ForgingAction {
    START,
    STRIKE,
    FINISH,
    CANCEL
}
