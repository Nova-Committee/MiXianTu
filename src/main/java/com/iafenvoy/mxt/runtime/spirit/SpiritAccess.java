package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.resource.Resource;
import net.minecraft.core.Holder;

/**
 * A block entity that can exchange whole units of one resource at a time.
 * Both operations return the part of {@code amount} that could not be moved.
 */
public interface SpiritAccess {
    /**
     * Attempts to move one resource; the return value is the unaccepted remainder.
     */
    int add(Holder<Resource> resource, int amount, boolean simulate);

    /**
     * Attempts to extract one resource; the return value is the unavailable remainder.
     */
    int extract(Holder<Resource> resource, int amount, boolean simulate);

    static int requireNonNegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Spirit amount must not be negative");
        return amount;
    }
}
