package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/** The tick the next channel pulse of a host is due. */
public final class ChannelPulse extends DataStorage {
    public static final MapCodec<ChannelPulse> CODEC = Codec.DOUBLE.fieldOf("next_tick").xmap(ChannelPulse::new, ChannelPulse::nextTick);
    private double nextTick;

    public ChannelPulse(double nextTick) {
        this.nextTick = nextTick;
    }

    @Override
    public MapCodec<ChannelPulse> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new ChannelPulse(this.nextTick);
    }

    public double nextTick() {
        return this.nextTick;
    }

    public void set(double nextTick) {
        this.nextTick = nextTick;
        this.markDirty();
    }
}
