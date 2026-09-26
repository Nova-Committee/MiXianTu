package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A locked target. {@code target} is the UUID of the locked entity as a string, because a target may be offline or
 * out of range while the lock is still held.
 */
public final class TargetLockDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by; the target comes from whatever writes the value.
    public static final TargetLockDataStorage INSTANCE = new TargetLockDataStorage(new Constant(0.0D), Optional.empty());
    public static final MapCodec<TargetLockDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("range").forGetter(TargetLockDataStorage::range),
            Codec.STRING.optionalFieldOf("target").forGetter(TargetLockDataStorage::target)
    ).apply(i, TargetLockDataStorage::new));
    private final NumberProvider range;
    private Optional<String> target;

    private TargetLockDataStorage(NumberProvider range, Optional<String> target) {
        this.range = range;
        this.target = target;
    }

    @Override
    public MapCodec<TargetLockDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new TargetLockDataStorage(this.range, this.target);
    }

    public NumberProvider range() {
        return this.range;
    }

    public Optional<String> target() {
        return this.target;
    }

    public boolean locked() {
        return this.target.isPresent();
    }

    public void lock(String target) {
        this.target = Optional.of(target);
        this.markDirty();
    }

    public void clear() {
        this.target = Optional.empty();
        this.markDirty();
    }
}
