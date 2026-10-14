package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The four long-lived soul numbers. Nothing reads them yet - only reincarnation writes them, through the switch
 * that decides whether they survive a rebirth - so this is the storage the soul system will claim.
 */
public final class SoulStatsAttachment extends AbstractChangeTrackable {
    public static final MapCodec<SoulStatsAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.lenientOptionalFieldOf("karma", 0.0D).forGetter(SoulStatsAttachment::karma),
            Codec.DOUBLE.lenientOptionalFieldOf("heart_demon", 0.0D).forGetter(SoulStatsAttachment::heartDemon),
            Codec.DOUBLE.lenientOptionalFieldOf("strength", 0.0D).forGetter(SoulStatsAttachment::soulStrength),
            Codec.DOUBLE.lenientOptionalFieldOf("sense_range", 0.0D).forGetter(SoulStatsAttachment::soulSenseRange)
    ).apply(i, SoulStatsAttachment::new));
    private double karma, heartDemon, soulStrength, soulSenseRange;

    public SoulStatsAttachment() {
        this(0.0D, 0.0D, 0.0D, 0.0D);
    }

    private SoulStatsAttachment(double karma, double heartDemon, double soulStrength, double soulSenseRange) {
        validate(karma, "Karma");
        validate(heartDemon, "Heart demon");
        validate(soulStrength, "Soul strength");
        validateRange(soulSenseRange);
        this.karma = karma;
        this.heartDemon = heartDemon;
        this.soulStrength = soulStrength;
        this.soulSenseRange = soulSenseRange;
    }

    public double karma() {
        return this.karma;
    }

    public double heartDemon() {
        return this.heartDemon;
    }

    public double soulStrength() {
        return this.soulStrength;
    }

    public double soulSenseRange() {
        return this.soulSenseRange;
    }

    // Reincarnation with the soul switches off: the four numbers go back to their defaults.
    public void resetSoul() {
        this.karma = 0.0D;
        this.heartDemon = 0.0D;
        this.soulStrength = 0.0D;
        this.soulSenseRange = 0.0D;
        this.markDirty();
    }

    public void setKarma(double value) {
        validate(value, "Karma");
        this.karma = value;
        this.markDirty();
    }

    public void setHeartDemon(double value) {
        validate(value, "Heart demon");
        this.heartDemon = value;
        this.markDirty();
    }

    public void setSoulStrength(double value) {
        validate(value, "Soul strength");
        this.soulStrength = value;
        this.markDirty();
    }

    public void setSoulSenseRange(double value) {
        validateRange(value);
        this.soulSenseRange = value;
        this.markDirty();
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void validateRange(double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Soul sense range must be non-negative");
    }
}
