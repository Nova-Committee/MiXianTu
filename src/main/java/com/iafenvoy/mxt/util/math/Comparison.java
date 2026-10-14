package com.iafenvoy.mxt.util.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Origins-compatible comparison used by generic datapack conditions.
 */
public record Comparison(CompareOperation operation, double compareTo) {
    public static final MapCodec<Comparison> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CompareOperation.CODEC.fieldOf("comparison").forGetter(Comparison::operation),
            Codec.DOUBLE.fieldOf("compare_to").forGetter(Comparison::compareTo)
    ).apply(i, Comparison::new));

    public boolean compare(double value) {
        return this.operation.compare(value, this.compareTo);
    }

    public boolean compare(int value) {
        return this.compare((double) value);
    }
}
