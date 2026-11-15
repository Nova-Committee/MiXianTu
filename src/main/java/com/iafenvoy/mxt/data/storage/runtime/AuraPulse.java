package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

// Retained under mxt:aura_pulse to read old hosts; current cadence tracking no longer writes it.
public final class AuraPulse extends DataStorage {
    public static final MapCodec<AuraPulse> CODEC = Codec.DOUBLE.fieldOf("next_tick").xmap(AuraPulse::new, AuraPulse::nextTick);
    private double nextTick;

    public AuraPulse(double nextTick) {
        this.nextTick = nextTick;
    }

    @Override
    public MapCodec<AuraPulse> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new AuraPulse(this.nextTick);
    }

    public double nextTick() {
        return this.nextTick;
    }

    public void set(double nextTick) {
        this.nextTick = nextTick;
        this.markDirty();
    }
}
