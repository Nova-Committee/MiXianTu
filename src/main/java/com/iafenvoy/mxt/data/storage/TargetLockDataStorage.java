package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A locked target. {@code target} is the UUID of the locked entity as a string, because a target may be offline
 * or out of range while the lock is still held.
 */
public record TargetLockDataStorage(NumberProvider range, Optional<String> target) implements DataStorage {
    public static final MapCodec<TargetLockDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("range").forGetter(TargetLockDataStorage::range),
            Codec.STRING.optionalFieldOf("target").forGetter(TargetLockDataStorage::target)
    ).apply(i, TargetLockDataStorage::new));

    @Override
    public MapCodec<TargetLockDataStorage> codec() {
        return CODEC;
    }

    public TargetLockDataStorage withTarget(String value) {
        return new TargetLockDataStorage(this.range, Optional.of(value));
    }
}