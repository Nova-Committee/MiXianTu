package com.iafenvoy.mxt.util.codec;

import com.iafenvoy.mxt.util.OptionalBoolean;
import com.iafenvoy.mxt.util.OptionalFloat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public final class OptionalCodecs {
    public static MapCodec<OptionalBoolean> boolCodec(String name) {
        return packBool(Codec.BOOL.optionalFieldOf(name));
    }

    public static MapCodec<OptionalBoolean> lenientBoolCodec(String name) {
        return packBool(Codec.BOOL.lenientOptionalFieldOf(name));
    }

    private static MapCodec<OptionalBoolean> packBool(MapCodec<Optional<Boolean>> codec) {
        return codec.xmap(o -> o.map(OptionalBoolean::of).orElseGet(OptionalBoolean::empty), o -> o.isPresent() ? Optional.of(o.getAsBoolean()) : Optional.empty());
    }

    public static MapCodec<OptionalInt> intCodec(String name) {
        return packInt(Codec.INT.optionalFieldOf(name));
    }

    public static MapCodec<OptionalInt> lenientIntCodec(String name) {
        return packInt(Codec.INT.lenientOptionalFieldOf(name));
    }

    public static MapCodec<OptionalInt> intCodec(int minInclusive, int maxInclusive, String name) {
        return packInt(Codec.intRange(minInclusive, maxInclusive).optionalFieldOf(name));
    }

    public static MapCodec<OptionalInt> lenientIntCodec(int minInclusive, int maxInclusive, String name) {
        return packInt(Codec.intRange(minInclusive, maxInclusive).lenientOptionalFieldOf(name));
    }

    public static MapCodec<OptionalInt> rgbColorCodec(String name) {
        return packInt(ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf(name));
    }

    private static MapCodec<OptionalInt> packInt(MapCodec<Optional<Integer>> codec) {
        return codec.xmap(o -> o.map(OptionalInt::of).orElseGet(OptionalInt::empty), o -> o.isPresent() ? Optional.of(o.getAsInt()) : Optional.empty());
    }

    public static MapCodec<OptionalLong> longCodec(String name) {
        return packLong(Codec.LONG.optionalFieldOf(name));
    }

    public static MapCodec<OptionalLong> lenientLongCodec(String name) {
        return packLong(Codec.LONG.lenientOptionalFieldOf(name));
    }

    public static MapCodec<OptionalLong> longCodec(long minInclusive, long maxInclusive, String name) {
        return packLong(MiscCodecs.longRange(minInclusive, maxInclusive).optionalFieldOf(name));
    }

    public static MapCodec<OptionalLong> lenientLongCodec(long minInclusive, long maxInclusive, String name) {
        return packLong(MiscCodecs.longRange(minInclusive, maxInclusive).lenientOptionalFieldOf(name));
    }

    private static MapCodec<OptionalLong> packLong(MapCodec<Optional<Long>> codec) {
        return codec.xmap(o -> o.map(OptionalLong::of).orElseGet(OptionalLong::empty), o -> o.isPresent() ? Optional.of(o.getAsLong()) : Optional.empty());
    }

    public static MapCodec<OptionalFloat> floatCodec(String name) {
        return packFloat(Codec.FLOAT.optionalFieldOf(name));
    }

    public static MapCodec<OptionalFloat> lenientFloatCodec(String name) {
        return packFloat(Codec.FLOAT.lenientOptionalFieldOf(name));
    }

    public static MapCodec<OptionalFloat> floatCodec(float minInclusive, float maxInclusive, String name) {
        return packFloat(Codec.floatRange(minInclusive, maxInclusive).optionalFieldOf(name));
    }

    public static MapCodec<OptionalFloat> lenientFloatCodec(float minInclusive, float maxInclusive, String name) {
        return packFloat(Codec.floatRange(minInclusive, maxInclusive).lenientOptionalFieldOf(name));
    }

    private static MapCodec<OptionalFloat> packFloat(MapCodec<Optional<Float>> codec) {
        return codec.xmap(o -> o.map(OptionalFloat::of).orElseGet(OptionalFloat::empty), o -> o.isPresent() ? Optional.of(o.getAsFloat()) : Optional.empty());
    }

    public static MapCodec<OptionalDouble> doubleCodec(String name) {
        return packDouble(Codec.DOUBLE.optionalFieldOf(name));
    }

    // For a double field whose base codec already carries its own rule, such as a positive-only number.
    public static MapCodec<OptionalDouble> doubleCodec(Codec<Double> codec, String name) {
        return packDouble(codec.optionalFieldOf(name));
    }

    public static MapCodec<OptionalDouble> lenientDoubleCodec(String name) {
        return packDouble(Codec.DOUBLE.lenientOptionalFieldOf(name));
    }

    public static MapCodec<OptionalDouble> doubleCodec(double minInclusive, double maxInclusive, String name) {
        return packDouble(Codec.doubleRange(minInclusive, maxInclusive).optionalFieldOf(name));
    }

    public static MapCodec<OptionalDouble> lenientDoubleCodec(double minInclusive, double maxInclusive, String name) {
        return packDouble(Codec.doubleRange(minInclusive, maxInclusive).lenientOptionalFieldOf(name));
    }

    private static MapCodec<OptionalDouble> packDouble(MapCodec<Optional<Double>> codec) {
        return codec.xmap(o -> o.map(OptionalDouble::of).orElseGet(OptionalDouble::empty), o -> o.isPresent() ? Optional.of(o.getAsDouble()) : Optional.empty());
    }
}
