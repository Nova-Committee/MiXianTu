package com.iafenvoy.mxt.runtime.spirit;

/**
 * A block entity that can exchange whole units of emitted spirit power.
 * Both operations return the part of {@code amount} that could not be moved.
 */
public interface SpiritAccess {
    int add(int amount, boolean simulate);

    int extract(int amount, boolean simulate);

    static int requireNonNegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Spirit amount must not be negative");
        return amount;
    }
}
