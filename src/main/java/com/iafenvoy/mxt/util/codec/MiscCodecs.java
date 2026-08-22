package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.function.Function;

public final class MiscCodecs {
    public static final Codec<Integer> COLOR = color(true), COLOR_NO_ALPHA = color(false);

    private static Codec<Integer> color(boolean alpha) {
        int digits = alpha ? 8 : 6;
        Codec<Integer> number = alpha ? signedOrUnsignedInt() : Codec.intRange(0, 0xFFFFFF);
        Codec<String> hexadecimal = Codec.STRING.comapFlatMap(value -> validateHexadecimal(value, digits), Function.identity());
        return Codec.either(number, hexadecimal).comapFlatMap(
                value -> value.map(DataResult::success, hexadecimalValue -> parseHexadecimal(hexadecimalValue, alpha)),
                Either::left);
    }

    /**
     * Accepts signed and unsigned JSON representations of the same 32 raw bits.
     */
    private static Codec<Integer> signedOrUnsignedInt() {
        return Codec.either(Codec.INT, longRange(0L, 0xFFFF_FFFFL)).flatXmap(
                value -> DataResult.success(value.map(Function.identity(), unsigned -> (int) (long) unsigned)),
                value -> DataResult.success(Either.left(value))
        );
    }

    private static DataResult<String> validateHexadecimal(String value, int digits) {
        if (!value.startsWith("#")) {
            return DataResult.error(() -> "Expected '#' followed by " + digits + " hexadecimal color digits: " + value);
        }
        String hexadecimal = value.substring(1);
        if (hexadecimal.length() != digits || hexadecimal.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            return DataResult.error(() -> "Expected '#' followed by " + digits + " hexadecimal color digits: " + value);
        }
        return DataResult.success(hexadecimal);
    }

    private static DataResult<Integer> parseHexadecimal(String value, boolean alpha) {
        try {
            return DataResult.success(alpha ? Integer.parseUnsignedInt(value, 16) : Integer.parseInt(value, 16));
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Invalid hexadecimal color: " + value);
        }
    }

    public static Codec<Long> longRange(final long minInclusive, final long maxInclusive) {
        final Function<Long, DataResult<Long>> checker = Codec.checkRange(minInclusive, maxInclusive);
        return Codec.LONG.flatXmap(checker, checker);
    }
}
