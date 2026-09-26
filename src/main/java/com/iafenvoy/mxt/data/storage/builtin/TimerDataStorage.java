package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A running duration. {@code ends_at} is the tick the timer ends at, so a reader compares it against the current tick
 * without having to know when the timer started.
 */
public final class TimerDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by; the duration and the end tick come from whatever writes it.
    public static final TimerDataStorage INSTANCE = new TimerDataStorage(new Constant(0.0D), Optional.empty());
    public static final MapCodec<TimerDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("duration").forGetter(TimerDataStorage::duration),
            Codec.DOUBLE.optionalFieldOf("ends_at").forGetter(TimerDataStorage::endsAt)
    ).apply(i, TimerDataStorage::new));
    private final NumberProvider duration;
    private Optional<Double> endsAt;

    private TimerDataStorage(NumberProvider duration, Optional<Double> endsAt) {
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

    public Optional<Double> endsAt() {
        return this.endsAt;
    }

    public boolean running() {
        return this.endsAt.isPresent();
    }

    public long remaining(long gameTime) {
        return this.endsAt.map(end -> Math.max(0L, Math.round(end) - gameTime)).orElse(0L);
    }

    public void start(double endsAt) {
        this.endsAt = Optional.of(endsAt);
        this.markDirty();
    }

    public void clear() {
        this.endsAt = Optional.empty();
        this.markDirty();
    }
}
