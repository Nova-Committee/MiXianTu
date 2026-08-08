package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record WeightedList(List<Entry> distribution) implements NumberProvider {
    public static final MapCodec<WeightedList> MAP_CODEC = Entry.MAP_CODEC.codec().listOf().fieldOf("distribution").xmap(WeightedList::new, WeightedList::distribution);

    public WeightedList {
        if (distribution.isEmpty()) throw new IllegalArgumentException("Weighted list requires at least one entry");
    }

    @Override
    public double evaluate(FormulaContext context) {
        long total = 0L;
        try {
            for (Entry entry : this.distribution) total = Math.addExact(total, entry.weight());
        } catch (ArithmeticException exception) {
            LOGGER.warn("Number provider WeightedList overflowed its total weight; using 0");
            return 0.0D;
        }
        if (total <= 0L) {
            LOGGER.warn("Number provider WeightedList has no positive weight; using 0");
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
        public static final MapCodec<Entry> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CODEC.fieldOf("data").forGetter(Entry::data),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(Entry::weight)
        ).apply(instance, Entry::new));
    }
}
