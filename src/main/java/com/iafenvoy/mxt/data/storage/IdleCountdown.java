package com.iafenvoy.mxt.data.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * How many ticks an idle beat has left, counted down by the beat itself. The length is resolved once, when the
 * beat begins, so the countdown is the length that beat was given and not a value read from the definition again
 * every tick.
 */
public record IdleCountdown(long remaining) implements RuntimeStorage {
    public static final MapCodec<IdleCountdown> CODEC = Codec.LONG.fieldOf("remaining").xmap(IdleCountdown::new, IdleCountdown::remaining);

    @Override
    public MapCodec<IdleCountdown> codec() {
        return CODEC;
    }
}
