package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent accumulated pill toxicity for one living entity. {@code decayRemainder} is the unfinished
 * 20-tick decay period; it is not toxicity and is only advanced while a positive decay rate is configured.
 */
public final class PillToxicityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<PillToxicityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.lenientOptionalFieldOf("toxicity", 0.0D).forGetter(PillToxicityAttachment::toxicity),
            Codec.INT.lenientOptionalFieldOf("decay_remainder", 0).forGetter(PillToxicityAttachment::decayRemainder)
    ).apply(i, PillToxicityAttachment::new));
    private double toxicity;
    private int decayRemainder;

    public PillToxicityAttachment() {
        this(0.0D, 0);
    }

    private PillToxicityAttachment(double toxicity, int decayRemainder) {
        if (!Double.isFinite(toxicity)) throw new IllegalArgumentException("Pill toxicity must be finite");
        this.toxicity = Math.max(0.0D, toxicity);
        this.decayRemainder = Math.max(0, decayRemainder);
    }

    public double toxicity() {
        return this.toxicity;
    }

    public int decayRemainder() {
        return this.decayRemainder;
    }

    public void set(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Pill toxicity must be finite");
        this.toxicity = Math.max(0.0D, value);
        this.markDirty();
    }

    public double add(double amount) {
        this.set(this.toxicity + amount);
        return this.toxicity;
    }

    public void setDecayRemainder(int remainder) {
        this.decayRemainder = Math.max(0, remainder);
        this.markDirty();
    }
}
