package com.iafenvoy.mxt.runtime.curse;

import net.minecraft.resources.Identifier;

/**
 * Mutable state for one named curse on a holder. A negative expiry means permanent.
 */
public record CurseInstance(Identifier id, int stacks, long appliedAt, long expiresAt, String source) {
    public boolean expiredAt(long gameTime) {
        return this.expiresAt >= 0L && gameTime >= this.expiresAt;
    }
}
