package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * The tick the next aura pulse of a host is due. The cadence is what times a pulse now, so nothing writes this
 * kind; it stays registered so a value an older save carries still resolves instead of failing the whole holder.
 */
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
