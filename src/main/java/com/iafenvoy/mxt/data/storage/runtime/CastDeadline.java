package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * The tick the current cast of a host finishes at; the tick the value was written is when that cast started, which
 * is what a cast bar draws against. The {@code NO_CAST} sentinel is what "nothing pending" reads as, so a cleared
 * deadline is stored state rather than a missing entry.
 */
public final class CastDeadline extends DataStorage {
    public static final double NO_CAST = Double.MAX_VALUE;
    public static final MapCodec<CastDeadline> CODEC = Codec.DOUBLE.fieldOf("ends_at").xmap(CastDeadline::new, CastDeadline::endsAt);
    private double endsAt;

    public CastDeadline(double endsAt) {
        this.endsAt = endsAt;
    }

    @Override
    public MapCodec<CastDeadline> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new CastDeadline(this.endsAt);
    }

    public double endsAt() {
        return this.endsAt;
    }

    public boolean pending() {
        return this.endsAt < NO_CAST;
    }

    // A deadline that is neither the "no cast" sentinel nor still in the future.
    public boolean due(long gameTime) {
        return this.endsAt <= gameTime;
    }

    public void start(double endsAt) {
        this.endsAt = endsAt;
        this.markDirty();
    }

    public void clear() {
        this.endsAt = NO_CAST;
        this.markDirty();
    }
}
