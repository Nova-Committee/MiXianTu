package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.util.Trio.First;
import com.iafenvoy.mxt.util.Trio.Second;
import com.iafenvoy.mxt.util.Trio.Third;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.Optional;
import java.util.function.Function;

/**
 * A value that contains exactly one of three possible types.
 *
 * <p>Like {@link Either}, the active branch is preserved by mapping and codec
 * operations; no nullable sentinel or parallel optional fields are needed.</p>
 */
public sealed interface Trio<F, S, T> permits First, Second, Third {
    /**
     * Builds a three-way codec while preserving the supplied codec order.
     */
    static <F, S, T> Codec<Trio<F, S, T>> codec(Codec<F> first, Codec<S> second, Codec<T> third) {
        return new TrioCodec<>(first, second, third);
    }

    static <F, S, T> Trio<F, S, T> first(F value) {
        return new First<>(value);
    }

    static <F, S, T> Trio<F, S, T> second(S value) {
        return new Second<>(value);
    }

    static <F, S, T> Trio<F, S, T> third(T value) {
        return new Third<>(value);
    }

    <R> R map(Function<? super F, ? extends R> first, Function<? super S, ? extends R> second, Function<? super T, ? extends R> third);

    default <NF> Trio<NF, S, T> mapFirst(Function<? super F, ? extends NF> mapper) {
        return this.map(value -> first(mapper.apply(value)), Trio::second, Trio::third);
    }

    default <NS> Trio<F, NS, T> mapSecond(Function<? super S, ? extends NS> mapper) {
        return this.map(Trio::first, value -> second(mapper.apply(value)), Trio::third);
    }

    default <NT> Trio<F, S, NT> mapThird(Function<? super T, ? extends NT> mapper) {
        return this.map(Trio::first, Trio::second, value -> third(mapper.apply(value)));
    }

    default Optional<F> first() {
        return this.map(Optional::of, _ -> Optional.empty(), _ -> Optional.empty());
    }

    default Optional<S> second() {
        return this.map(_ -> Optional.empty(), Optional::of, _ -> Optional.empty());
    }

    default Optional<T> third() {
        return this.map(_ -> Optional.empty(), _ -> Optional.empty(), Optional::of);
    }

    record First<F, S, T>(F value) implements Trio<F, S, T> {
        @Override
        public <R> R map(Function<? super F, ? extends R> first, Function<? super S, ? extends R> second, Function<? super T, ? extends R> third) {
            return first.apply(this.value);
        }
    }

    record Second<F, S, T>(S value) implements Trio<F, S, T> {
        @Override
        public <R> R map(Function<? super F, ? extends R> first, Function<? super S, ? extends R> second, Function<? super T, ? extends R> third) {
            return second.apply(this.value);
        }
    }

    record Third<F, S, T>(T value) implements Trio<F, S, T> {
        @Override
        public <R> R map(Function<? super F, ? extends R> first, Function<? super S, ? extends R> second, Function<? super T, ? extends R> third) {
            return third.apply(this.value);
        }
    }

    record TrioCodec<F, S, T>(Codec<F> first, Codec<S> second, Codec<T> third) implements Codec<Trio<F, S, T>> {
        @Override
        public <A> DataResult<Pair<Trio<F, S, T>, A>> decode(DynamicOps<A> ops, A input) {
            final DataResult<Pair<Trio<F, S, T>, A>> firstRead = this.first.decode(ops, input).map(pair -> pair.mapFirst(Trio::first));
            if (firstRead.isSuccess()) return firstRead;
            final DataResult<Pair<Trio<F, S, T>, A>> secondRead = this.second.decode(ops, input).map(pair -> pair.mapFirst(Trio::second));
            if (secondRead.isSuccess()) return secondRead;
            final DataResult<Pair<Trio<F, S, T>, A>> thirdRead = this.third.decode(ops, input).map(pair -> pair.mapFirst(Trio::third));
            if (thirdRead.isSuccess()) return thirdRead;
            if (firstRead.hasResultOrPartial()) return firstRead;
            if (secondRead.hasResultOrPartial()) return secondRead;
            if (thirdRead.hasResultOrPartial()) return thirdRead;
            return DataResult.error(() -> "Failed to parse trio. First: " + firstRead.error().orElseThrow().message() + "; Second: " + secondRead.error().orElseThrow().message() + "; Third: " + thirdRead.error().orElseThrow().message());
        }

        @Override
        public <A> DataResult<A> encode(Trio<F, S, T> input, DynamicOps<A> ops, A prefix) {
            return input.map(
                    value -> this.first.encode(value, ops, prefix),
                    value -> this.second.encode(value, ops, prefix),
                    value -> this.third.encode(value, ops, prefix)
            );
        }
    }
}
