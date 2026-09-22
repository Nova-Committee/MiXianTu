package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A running duration. {@code endsAt} is the tick the timer ends at, so a reader compares it against the current tick
 * without having to know when the timer started.
 */
public record TimerDataStorage(NumberProvider duration, Optional<Double> endsAt) implements DataStorage {
    public static final MapCodec<TimerDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("duration").forGetter(TimerDataStorage::duration),
            Codec.DOUBLE.optionalFieldOf("ends_at").forGetter(TimerDataStorage::endsAt)
    ).apply(i, TimerDataStorage::new));

    @Override
    public MapCodec<TimerDataStorage> codec() {
        return CODEC;
    }

    public TimerDataStorage withEndsAt(double value) {
        return new TimerDataStorage(this.duration, Optional.of(value));
    }
}