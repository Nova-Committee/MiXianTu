package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent accumulated pill toxicity for one living entity.
 */
public final class PillToxicityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<PillToxicityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("toxicity", 0.0D).forGetter(PillToxicityAttachment::toxicity)
    ).apply(i, PillToxicityAttachment::new));
    private double toxicity;

    public PillToxicityAttachment() {
        this(0.0D);
    }

    private PillToxicityAttachment(double toxicity) {
        if (!Double.isFinite(toxicity)) throw new IllegalArgumentException("Pill toxicity must be finite");
        this.toxicity = Math.max(0.0D, toxicity);
    }

    public double toxicity() {
        return this.toxicity;
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
}
