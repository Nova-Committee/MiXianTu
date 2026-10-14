package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The lifespan ledger: how much life is left and the most this body was ever granted in this life. Both numbers
 * are written together by the one service that owns the rules, so a body cannot end up with a half-updated account.
 */
public final class LifespanAttachment extends AbstractChangeTrackable {
    public static final MapCodec<LifespanAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.lenientOptionalFieldOf("lifespan_remaining", -1L).forGetter(LifespanAttachment::lifespanRemaining),
            Codec.LONG.lenientOptionalFieldOf("lifespan_total", -1L).forGetter(LifespanAttachment::lifespanTotal)
    ).apply(i, LifespanAttachment::new));
    private long lifespanRemaining, lifespanTotal;

    public LifespanAttachment() {
        this(-1L, -1L);
    }

    private LifespanAttachment(long lifespanRemaining, long lifespanTotal) {
        if (lifespanRemaining < -1L || lifespanTotal < -1L)
            throw new IllegalArgumentException("Invalid lifespan ledger");
        this.lifespanRemaining = lifespanRemaining;
        this.lifespanTotal = lifespanTotal;
    }

    public long lifespanRemaining() {
        return this.lifespanRemaining;
    }

    // The most this body was ever granted in one life; only a set rewrites it downwards. Negative means the
    // account was never opened, which is how a finished life is told from one that was never started.
    public long lifespanTotal() {
        return this.lifespanTotal;
    }

    public void setLifespan(long remaining, long total) {
        if (remaining < -1L || total < -1L)
            throw new IllegalArgumentException("Lifespan cannot be less than -1");
        this.lifespanRemaining = remaining;
        this.lifespanTotal = total;
        this.markDirty();
    }
}
