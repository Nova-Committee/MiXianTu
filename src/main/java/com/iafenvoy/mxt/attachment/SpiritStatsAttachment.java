package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persisted long-lived spirit statistics unrelated to the cultivation chain.
 */
public final class SpiritStatsAttachment extends ShouldSyncAttachment {
    public static final MapCodec<SpiritStatsAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.lenientOptionalFieldOf("lifespan_remaining", -1L).forGetter(SpiritStatsAttachment::lifespanRemaining),
            Codec.LONG.lenientOptionalFieldOf("lifespan_total", -1L).forGetter(SpiritStatsAttachment::lifespanTotal),
            SoulState.CODEC.lenientOptionalFieldOf("soul", SoulState.EMPTY).forGetter(SpiritStatsAttachment::soulState)
    ).apply(i, SpiritStatsAttachment::new));

    private long lifespanRemaining;
    private long lifespanTotal;
    private SoulState soulState;

    public SpiritStatsAttachment() {
        this(-1L, -1L, SoulState.EMPTY);
    }

    private SpiritStatsAttachment(long lifespanRemaining, long lifespanTotal, SoulState soulState) {
        if (lifespanRemaining < -1L || lifespanTotal < -1L) throw new IllegalArgumentException("Invalid spirit state");
        this.lifespanRemaining = lifespanRemaining;
        this.lifespanTotal = lifespanTotal;
        this.soulState = soulState;
    }

    public long lifespanRemaining() {
        return this.lifespanRemaining;
    }

    // The most this body was ever granted in one life; only a set rewrites it downwards. Negative means the
    // account was never opened, which is how a finished life is told from one that was never started.
    public long lifespanTotal() {
        return this.lifespanTotal;
    }

    public double karma() {
        return this.soulState.karma();
    }

    public double heartDemon() {
        return this.soulState.heartDemon();
    }

    public double soulStrength() {
        return this.soulState.soulStrength();
    }

    public double soulSenseRange() {
        return this.soulState.soulSenseRange();
    }

    public void setLifespan(long remaining, long total) {
        if (remaining < -1L || total < -1L)
            throw new IllegalArgumentException("Lifespan cannot be less than -1");
        this.lifespanRemaining = remaining;
        this.lifespanTotal = total;
        this.markDirty();
    }

    // Reincarnation with the soul switches off: the four soul numbers go back to their defaults.
    public void resetSoul() {
        this.soulState = SoulState.EMPTY;
        this.markDirty();
    }

    public void setKarma(double value) {
        validate(value, "Karma");
        this.soulState = new SoulState(value, this.heartDemon(), this.soulStrength(), this.soulSenseRange());
        this.markDirty();
    }

    public void setHeartDemon(double value) {
        validate(value, "Heart demon");
        this.soulState = new SoulState(this.karma(), value, this.soulStrength(), this.soulSenseRange());
        this.markDirty();
    }

    public void setSoulStrength(double value) {
        validate(value, "Soul strength");
        this.soulState = new SoulState(this.karma(), this.heartDemon(), value, this.soulSenseRange());
        this.markDirty();
    }

    public void setSoulSenseRange(double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Soul sense range must be non-negative");
        this.soulState = new SoulState(this.karma(), this.heartDemon(), this.soulStrength(), value);
        this.markDirty();
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private SoulState soulState() {
        return this.soulState;
    }

    private record SoulState(double karma, double heartDemon, double soulStrength, double soulSenseRange) {
        private static final SoulState EMPTY = new SoulState(0.0D, 0.0D, 0.0D, 0.0D);
        private static final Codec<SoulState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.lenientOptionalFieldOf("karma", 0.0D).forGetter(SoulState::karma),
                Codec.DOUBLE.lenientOptionalFieldOf("heart_demon", 0.0D).forGetter(SoulState::heartDemon),
                Codec.DOUBLE.lenientOptionalFieldOf("strength", 0.0D).forGetter(SoulState::soulStrength),
                Codec.DOUBLE.lenientOptionalFieldOf("sense_range", 0.0D).forGetter(SoulState::soulSenseRange)
        ).apply(i, SoulState::new));

        private SoulState {
            if (!Double.isFinite(karma) || !Double.isFinite(heartDemon) || !Double.isFinite(soulStrength)
                    || !Double.isFinite(soulSenseRange) || soulSenseRange < 0.0D)
                throw new IllegalArgumentException("Invalid soul state");
        }
    }
}
