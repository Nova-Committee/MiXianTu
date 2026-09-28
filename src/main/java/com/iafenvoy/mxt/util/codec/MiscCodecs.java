package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec2;

import java.util.List;
import java.util.function.Function;

public final class MiscCodecs {
    /**
     * A finite, non-negative number. Written as a codec rather than as a check at the point of use, so a negative
     * sign or a {@code NaN} is refused while the pack loads. {@code NaN} fails the upper bound because
     * {@link Double#compareTo} orders it above everything.
     */
    public static final Codec<Double> NON_NEGATIVE = Codec.doubleRange(0.0D, Double.MAX_VALUE);

    // Two element [x, z]; secret realm borders use it for their center.
    public static final Codec<Vec2> HORIZONTAL_PAIR = Codec.DOUBLE.listOf().comapFlatMap(
            values -> values.size() == 2
                    ? DataResult.success(new Vec2(values.getFirst().floatValue(), values.get(1).floatValue()))
                    : DataResult.error(() -> "Expected [x, z] with exactly two numbers"),
            value -> List.of((double) value.x, (double) value.y));

    // A bare JSON string is a translation key and an object is a full component, so a definition stays readable
    // without losing styling.
    public static final Codec<Component> TRANSLATABLE_COMPONENT = Codec.either(Codec.STRING, ComponentSerialization.CODEC)
            .xmap(e -> e.map(Component::translatable, Function.identity()), Either::right);

    /**
     * Two fields read as one group: {@code RecordCodecBuilder.group} stops at sixteen components, so a definition
     * with more fields pairs two of them and stays under the limit. The JSON keys are unchanged.
     */
    public static <A, B> MapCodec<Pair<A, B>> pair(MapCodec<A> first, MapCodec<B> second) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                first.forGetter(Pair::getFirst),
                second.forGetter(Pair::getSecond)
        ).apply(i, Pair::of));
    }

    public static Codec<Long> longRange(final long minInclusive, final long maxInclusive) {
        final Function<Long, DataResult<Long>> checker = Codec.checkRange(minInclusive, maxInclusive);
        return Codec.LONG.flatXmap(checker, checker);
    }

    public static <T> Codec<List<T>> combineCodec(Codec<T> codec) {
        return Codec.either(codec, AutoIgnoreListCodec.create(codec)).xmap(x -> x.map(List::of, l -> l), l -> l.size() == 1 ? Either.left(l.getFirst()) : Either.right(l));
    }
}
