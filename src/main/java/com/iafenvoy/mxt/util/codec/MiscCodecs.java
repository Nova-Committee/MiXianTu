package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.phys.Vec2;

import java.util.List;
import java.util.function.Function;

public final class MiscCodecs {
    public static final Codec<Integer> COLOR = color(true), COLOR_NO_ALPHA = color(false);

    /**
     * A finite, non-negative number, for the multipliers a definition applies to something. Written as a codec
     * rather than as a check at the point of use, so a negative sign or a {@code NaN} is refused while the pack
     * loads instead of quietly scaling damage at runtime. {@code NaN} fails the upper bound because
     * {@link Double#compareTo} orders it above everything.
     */
    public static final Codec<Double> NON_NEGATIVE = Codec.doubleRange(0.0D, Double.MAX_VALUE);

    /**
     * A two element {@code [x, z]} pair. Realm borders use it for their center.
     */
    public static final Codec<Vec2> HORIZONTAL_PAIR = Codec.DOUBLE.listOf().comapFlatMap(
            values -> values.size() == 2
                    ? DataResult.success(new Vec2(values.getFirst().floatValue(), values.get(1).floatValue()))
                    : DataResult.error(() -> "Expected [x, z] with exactly two numbers"),
            value -> List.of((double) value.x, (double) value.y));

    /**
     * A datapack display string: a bare JSON string is a translation key, an object is a full component, so a
     * definition stays readable without losing styling when a pack needs it.
     */
    public static final Codec<Component> TRANSLATABLE_COMPONENT = Codec.either(Codec.STRING, ComponentSerialization.CODEC)
            .xmap(value -> value.map(Component::translatable, component -> component), Either::right);

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
