package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent accumulated pill toxicity for one living entity.
 */
public final class PillToxicityData {
    public static final MapCodec<PillToxicityData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("toxicity", 0.0D).forGetter(PillToxicityData::toxicity)
    ).apply(instance, PillToxicityData::decode));
    public static final Codec<PillToxicityData> CODEC = MAP_CODEC.codec();
    private double toxicity;

    public PillToxicityData() {
        this(0.0D);
    }

    private PillToxicityData(double toxicity) {
        this.set(toxicity);
    }

    private static PillToxicityData decode(double toxicity) {
        return new PillToxicityData(toxicity);
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
