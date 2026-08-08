package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.data.curse.Curse;
import net.minecraft.core.Holder;

/**
 * Mutable state for one named curse on a holder. A negative expiry means permanent.
 */
public record CurseInstance(Holder<Curse> curse, int stacks, long appliedAt, long expiresAt, String source) {
    public boolean expiredAt(long gameTime) {
        return this.expiresAt >= 0L && gameTime >= this.expiresAt;
    }
}
