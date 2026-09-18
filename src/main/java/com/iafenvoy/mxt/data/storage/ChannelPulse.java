package com.iafenvoy.mxt.data.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * The tick the next channel pulse of a host is due.
 */
public record ChannelPulse(double nextTick) implements RuntimeStorage {
    public static final MapCodec<ChannelPulse> CODEC = Codec.DOUBLE.fieldOf("next_tick").xmap(ChannelPulse::new, ChannelPulse::nextTick);

    @Override
    public MapCodec<ChannelPulse> codec() {
        return CODEC;
    }
}