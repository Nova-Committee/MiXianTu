package com.iafenvoy.mxt.network.payload;

/** Multiple player-trade state changes share one enum-backed payload. */
public enum PlayerTradeAction {
    ACCEPT,
    CANCEL_ACCEPT,
    CLOSE
}
