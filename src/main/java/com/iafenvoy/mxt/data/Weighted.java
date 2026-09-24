package com.iafenvoy.mxt.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * One entry of a weighted list, shared by every weighted choice in the mod. A negative weight counts as 0, and a
 * list whose total is 0 is picked from uniformly, so a table written wrong still runs instead of failing the load.
 */
public record Weighted<T>(T value, int weight) {
    public static <T> MapCodec<Weighted<T>> mapCodec(Codec<T> valueCodec) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                valueCodec.fieldOf("value").forGetter(Weighted::value),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Weighted::weight)
        ).apply(i, Weighted::new));
    }

    public static <T> Codec<Weighted<T>> codec(Codec<T> valueCodec) {
        return mapCodec(valueCodec).codec();
    }

    // Null only for an empty list, which an optional field may leave behind.
    public static <T> Weighted<T> select(List<Weighted<T>> entries, RandomSource random) {
        return select(entries, entries.stream().mapToLong(entry -> Math.max(0, entry.weight())).sum(), random);
    }

    // For callers that cache the total instead of summing on every pick.
    public static <T> Weighted<T> select(List<Weighted<T>> entries, long total, RandomSource random) {
        if (entries.isEmpty()) return null;
        if (total <= 0L) return entries.get(random.nextInt(entries.size()));
        long selected = (long) (random.nextDouble() * total);
        for (Weighted<T> entry : entries) {
            selected -= Math.max(0, entry.weight());
            if (selected < 0L) return entry;
        }
        return entries.getLast();
    }
}
