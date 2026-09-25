package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.RuntimeStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * How many ticks a waiting beat has left before its timeout, counted down by the beat itself. The length is
 * resolved once, when the beat begins, exactly like an idle beat's own countdown.
 */
public record WaitCountdown(long remaining) implements RuntimeStorage {
    public static final MapCodec<WaitCountdown> CODEC = Codec.LONG.fieldOf("remaining").xmap(WaitCountdown::new, WaitCountdown::remaining);

    @Override
    public MapCodec<WaitCountdown> codec() {
        return CODEC;
    }
}
