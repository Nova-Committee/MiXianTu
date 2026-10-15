package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.OptionalDouble;

/**
 * A running duration. {@code ends_at} is the tick the timer ends at, so a reader compares it against the current tick
 * without having to know when the timer started.
 */
public final class TimerDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by; the duration and the end tick come from whatever writes it.
    public static final TimerDataStorage INSTANCE = new TimerDataStorage(new Constant(0.0D), OptionalDouble.empty());
    public static final MapCodec<TimerDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("duration").forGetter(TimerDataStorage::duration),
            OptionalCodecs.doubleCodec("ends_at").forGetter(TimerDataStorage::endsAt)
    ).apply(i, TimerDataStorage::new));
    private final NumberProvider duration;
    private OptionalDouble endsAt;

    private TimerDataStorage(NumberProvider duration, OptionalDouble endsAt) {
        this.duration = duration;
        this.endsAt = endsAt;
    }

    @Override
    public MapCodec<TimerDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new TimerDataStorage(this.duration, this.endsAt);
    }

    public NumberProvider duration() {
        return this.duration;
    }

    public OptionalDouble endsAt() {
        return this.endsAt;
    }

    public boolean running() {
        return this.endsAt.isPresent();
    }

    public long remaining(long gameTime) {
        return this.endsAt.isPresent() ? Math.max(0L, Math.round(this.endsAt.getAsDouble()) - gameTime) : 0L;
    }

    public void start(double endsAt) {
        this.endsAt = OptionalDouble.of(endsAt);
        this.markDirty();
    }

    public void clear() {
        this.endsAt = OptionalDouble.empty();
        this.markDirty();
    }
}
