package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * How many ticks a waiting beat has left before its timeout, counted down by the beat itself. The length is
 * resolved once, when the beat begins, exactly like an idle beat's own countdown.
 */
public final class WaitCountdown extends DataStorage {
    public static final MapCodec<WaitCountdown> CODEC = Codec.LONG.fieldOf("remaining").xmap(WaitCountdown::new, WaitCountdown::remaining);
    private long remaining;

    public WaitCountdown(long remaining) {
        this.remaining = remaining;
    }

    @Override
    public MapCodec<WaitCountdown> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new WaitCountdown(this.remaining);
    }

    public long remaining() {
        return this.remaining;
    }

    public void set(long remaining) {
        this.remaining = remaining;
        this.markDirty();
    }
}
