package com.iafenvoy.mxt.util.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.function.DoubleSupplier;

public record ObjectComparison<T extends DoubleSupplier>(CompareOperation operation, T compareTo) {
    public static <T extends DoubleSupplier> MapCodec<ObjectComparison<T>> codec(Codec<T> elementCodec) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                CompareOperation.CODEC.fieldOf("comparison").forGetter(ObjectComparison::operation),
                elementCodec.fieldOf("compare_to").forGetter(ObjectComparison::compareTo)
        ).apply(i, ObjectComparison::new));
    }

    public boolean compare(T value) {
        return this.operation.compare(value.getAsDouble(), this.compareTo.getAsDouble());
    }
}
