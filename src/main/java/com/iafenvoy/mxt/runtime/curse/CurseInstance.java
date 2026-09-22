package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.data.curse.Curse;
import net.minecraft.core.Holder;

/**
 * Mutable state for one named curse on a holder; a negative expiry means permanent. Who keeps the instance alive
 * is not part of it: that is the attachment's source ledger, shared with ability grants.
 */
public record CurseInstance(Holder<Curse> curse, int stacks, long appliedAt, long expiresAt) {
    public boolean expiredAt(long gameTime) {
        return this.expiresAt >= 0L && gameTime >= this.expiresAt;
    }
}
