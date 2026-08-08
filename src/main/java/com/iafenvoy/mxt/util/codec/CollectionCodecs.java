package com.iafenvoy.mxt.util.codec;

import com.google.common.collect.ImmutableListMultimap.Builder;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Codec adapters for the immutable collections produced by datapack codecs.
 */
public final class CollectionCodecs {
    private CollectionCodecs() {
    }

    public static <K, V> Codec<Map<K, V>> map(Codec<K> keyCodec, Codec<V> valueCodec) {
        return AutoIgnoreMapCodec.create(keyCodec, valueCodec);
    }

    public static <V> Codec<List<V>> list(Codec<V> elementCodec) {
        return AutoIgnoreListCodec.create(elementCodec);
    }

    public static <K, V> Codec<Multimap<K, V>> multiMap(Codec<K> keyCodec, Codec<V> valueCodec) {
        return new AutoIgnoreMapCodec<>(keyCodec, valueCodec.listOf()).xmap(values -> {
            Builder<K, V> builder = ImmutableListMultimap.builder();
            values.forEach(builder::putAll);
            return builder.build();
        }, map -> Multimaps.asMap(map).entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> List.copyOf(entry.getValue()), (a, b) -> b, LinkedHashMap::new)));
    }

    public static <K> Codec<Object2DoubleMap<K>> doubleMap(Codec<K> keyCodec) {
        return map(keyCodec, Codec.DOUBLE).xmap(Object2DoubleOpenHashMap::new, Object2DoubleOpenHashMap::new);
    }

    public static <K> Codec<Object2IntMap<K>> intMap(Codec<K> keyCodec) {
        return map(keyCodec, Codec.INT).xmap(Object2IntOpenHashMap::new, Object2IntOpenHashMap::new);
    }

    public static <K> Codec<Object2LongMap<K>> longMap(Codec<K> keyCodec) {
        return map(keyCodec, Codec.LONG).xmap(Object2LongOpenHashMap::new, Object2LongOpenHashMap::new);
    }
}
