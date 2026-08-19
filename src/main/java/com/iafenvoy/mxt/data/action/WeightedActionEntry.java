package com.iafenvoy.mxt.data.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * A data-driven action choice with the same JSON shape as Origins.
 */
public record WeightedActionEntry<T>(T element, int weight) {
    public static <T> Codec<WeightedActionEntry<T>> codec(Codec<T> elementCodec) {
        return RecordCodecBuilder.create(i -> i.group(
                elementCodec.fieldOf("element").forGetter(WeightedActionEntry::element),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(WeightedActionEntry::weight)
        ).apply(i, WeightedActionEntry::new));
    }

    public static <T> WeightedActionEntry<T> select(List<WeightedActionEntry<T>> entries, RandomSource random) {
        long total = entries.stream().mapToLong(entry -> Math.max(0, entry.weight())).sum();
        if (entries.isEmpty()) return null;
        if (total <= 0L) return entries.get(random.nextInt(entries.size()));
        long selected = (long) (random.nextDouble() * total);
        for (WeightedActionEntry<T> entry : entries) {
            selected -= Math.max(0, entry.weight());
            if (selected < 0L) return entry;
        }
        return entries.getLast();
    }
}
