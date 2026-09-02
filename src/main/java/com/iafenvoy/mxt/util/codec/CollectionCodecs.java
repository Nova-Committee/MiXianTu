package com.iafenvoy.mxt.util.codec;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableListMultimap.Builder;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Codec adapters for the immutable collections produced by datapack codecs.
 */
public final class CollectionCodecs {
    public static <K, V> Codec<Map<K, V>> map(Codec<K> keyCodec, Codec<V> valueCodec) {
        return AutoIgnoreMapCodec.create(keyCodec, valueCodec);
    }

    public static <T> Codec<List<T>> list(Codec<T> elementCodec) {
        return AutoIgnoreListCodec.create(elementCodec);
    }

    public static <T> Codec<Set<T>> set(Codec<T> elementCodec) {
        return elementCodec.listOf().xmap(Set::copyOf, List::copyOf);
    }

    public static <K, V> Codec<Multimap<K, V>> multiMap(Codec<K> keyCodec, Codec<V> valueCodec) {
        return new AutoIgnoreMapCodec<>(keyCodec, valueCodec.listOf()).xmap(values -> {
            Builder<K, V> builder = ImmutableListMultimap.builder();
            values.forEach(builder::putAll);
            return builder.build();
        }, map -> Multimaps.asMap(map).entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> List.copyOf(entry.getValue()), (a, b) -> b, LinkedHashMap::new)));
    }

    public static <K> Codec<Object2DoubleMap<K>> doubleMap(Codec<K> keyCodec) {
        return map(keyCodec, Codec.DOUBLE).xmap(Object2DoubleOpenHashMap::new, Function.identity());
    }

    public static <K> Codec<Object2IntMap<K>> intMap(Codec<K> keyCodec) {
        return map(keyCodec, Codec.INT).xmap(Object2IntOpenHashMap::new, Function.identity());
    }

    public static <K> Codec<Object2LongMap<K>> longMap(Codec<K> keyCodec) {
        return map(keyCodec, Codec.LONG).xmap(Object2LongOpenHashMap::new, Function.identity());
    }

    public static <V> Codec<Int2ObjectMap<V>> intObjectMap(Codec<V> valueCodec) {
        Codec<Integer> stringInteger = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "Invalid integer map key: " + value);
            }
        }, String::valueOf);
        return map(stringInteger, valueCodec).xmap(Int2ObjectOpenHashMap::new, Function.identity());
    }
}
