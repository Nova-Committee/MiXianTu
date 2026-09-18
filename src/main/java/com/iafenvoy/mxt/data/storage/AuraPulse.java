package com.iafenvoy.mxt.data.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * The tick the next aura pulse of a host is due.
 */
public record AuraPulse(double nextTick) implements RuntimeStorage {
    public static final MapCodec<AuraPulse> CODEC = Codec.DOUBLE.fieldOf("next_tick").xmap(AuraPulse::new, AuraPulse::nextTick);

    @Override
    public MapCodec<AuraPulse> codec() {
        return CODEC;
    }
}