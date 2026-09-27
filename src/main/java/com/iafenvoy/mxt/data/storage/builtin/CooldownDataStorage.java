package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How long a host stays on cooldown: {@code duration} is the length the last use actually got and {@code started_at}
 * is the tick it began. A length that was never written reads as nothing remaining.
 */
public final class CooldownDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by; the length is written by every payment.
    public static final CooldownDataStorage INSTANCE = new CooldownDataStorage(Optional.empty(), 0L);
    public static final MapCodec<CooldownDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("duration").forGetter(CooldownDataStorage::duration),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(CooldownDataStorage::startedAt)
    ).apply(i, CooldownDataStorage::new));
    private Optional<Double> duration;
    private long startedAt;

    private CooldownDataStorage(Optional<Double> duration, long startedAt) {
        this.duration = duration;
        this.startedAt = startedAt;
    }

    @Override
    public MapCodec<CooldownDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new CooldownDataStorage(this.duration, this.startedAt);
    }

    public Optional<Double> duration() {
        return this.duration;
    }

    public long startedAt() {
        return this.startedAt;
    }

    // The length the payment actually got, and the tick it started on.
    public void start(double duration, long gameTime) {
        this.duration = Optional.of(duration);
        this.startedAt = gameTime;
        this.markDirty();
    }

    public void clear() {
        this.duration = Optional.empty();
        this.startedAt = 0L;
        this.markDirty();
    }

    public long remaining(long gameTime) {
        return Math.max(0L, Math.round(this.duration.orElse(0.0D)) - (gameTime - this.startedAt));
    }

    // A cooldown written by content starts when it is written, the one moment a pack cannot state itself.
    @Override
    public void writtenAt(long gameTime) {
        this.startedAt = gameTime;
    }

    public boolean onCooldown(long gameTime) {
        return this.remaining(gameTime) > 0L;
    }
}
