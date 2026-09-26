package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * How many ticks an idle beat has left, counted down by the beat itself. The length is resolved once, when the beat
 * begins, so the countdown is the length that beat was given rather than a value re-read from the definition.
 */
public final class IdleCountdown extends DataStorage {
    public static final MapCodec<IdleCountdown> CODEC = Codec.LONG.fieldOf("remaining").xmap(IdleCountdown::new, IdleCountdown::remaining);
    private long remaining;

    public IdleCountdown(long remaining) {
        this.remaining = remaining;
    }

    @Override
    public MapCodec<IdleCountdown> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new IdleCountdown(this.remaining);
    }

    public long remaining() {
        return this.remaining;
    }

    public void set(long remaining) {
        this.remaining = remaining;
        this.markDirty();
    }
}
