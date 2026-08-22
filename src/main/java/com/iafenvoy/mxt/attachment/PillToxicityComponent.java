package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent accumulated pill toxicity for one living entity.
 */
public final class PillToxicityComponent {
    public static final MapCodec<PillToxicityComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("toxicity", 0.0D).forGetter(PillToxicityComponent::toxicity)
    ).apply(i, PillToxicityComponent::new));
    private double toxicity;

    public PillToxicityComponent() {
        this(0.0D);
    }

    private PillToxicityComponent(double toxicity) {
        this.set(toxicity);
    }

    public double toxicity() {
        return this.toxicity;
    }

    public void set(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Pill toxicity must be finite");
        this.toxicity = Math.max(0.0D, value);
    }

    public double add(double amount) {
        this.set(this.toxicity + amount);
        return this.toxicity;
    }
}
