package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Picks one entry by positive integer weight. The weights are fixed once the definition is
 * loaded, so the total is computed here instead of on every evaluation.
 */
public final class WeightedList implements NumberProvider {
    public static final MapCodec<WeightedList> MAP_CODEC = Entry.MAP_CODEC.codec().listOf().fieldOf("distribution").xmap(WeightedList::new, WeightedList::distribution);

    private final List<Entry> distribution;
    /**
     * Sum of all weights, or a non-positive value when the list overflows an {@code int} total and
     * the provider has to refuse to roll.
     */
    private final long total;

    public WeightedList(List<Entry> distribution) {
        if (distribution.isEmpty()) throw new IllegalArgumentException("Weighted list requires at least one entry");
        this.distribution = List.copyOf(distribution);
        long sum = 0L;
        try {
            for (Entry entry : this.distribution) sum = Math.addExact(sum, entry.weight());
        } catch (ArithmeticException exception) {
            sum = -1L;
        }
        this.total = sum;
    }

    public List<Entry> distribution() {
        return this.distribution;
    }

    @Override
    public double evaluate(FormulaContext context) {
        long total = this.total;
        if (total <= 0L) {
            LOGGER.warn("Number provider WeightedList has an invalid total weight; using 0");
            return 0.0D;
        }
        long selected = (long) (context.random().nextDouble() * total);
        for (Entry entry : this.distribution) {
            selected -= entry.weight();
            if (selected < 0L) return entry.data().evaluate(context);
        }
        return this.distribution.getLast().data().evaluate(context);
    }

    @Override
    public MapCodec<WeightedList> codec() {
        return MAP_CODEC;
    }

    public record Entry(NumberProvider data, int weight) {
        public static final MapCodec<Entry> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                CODEC.fieldOf("data").forGetter(Entry::data),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(Entry::weight)
        ).apply(i, Entry::new));
    }
}
