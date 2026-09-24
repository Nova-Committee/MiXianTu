package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.data.Weighted;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaDiagnostics;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * Picks one entry by non-negative integer weight. The weights are fixed once the definition is loaded, so the total
 * is computed here instead of on every evaluation.
 */
public final class WeightedList implements NumberProvider {
    public static final MapCodec<WeightedList> MAP_CODEC = Weighted.codec(CODEC).listOf().fieldOf("distribution")
            .flatXmap(WeightedList::decode, list -> DataResult.success(list.distribution()));

    private final List<Weighted<NumberProvider>> distribution;
    // Sum of all weights, or non-positive when every weight is 0 or the list overflows an int total.
    private final long total;

    public WeightedList(List<Weighted<NumberProvider>> distribution) {
        if (distribution.isEmpty()) throw new IllegalArgumentException("Weighted list requires at least one entry");
        this.distribution = List.copyOf(distribution);
        long sum = 0L;
        try {
            for (Weighted<NumberProvider> entry : this.distribution) sum = Math.addExact(sum, Math.max(0, entry.weight()));
        } catch (ArithmeticException exception) {
            sum = -1L;
        }
        this.total = sum;
    }

    public List<Weighted<NumberProvider>> distribution() {
        return this.distribution;
    }

    // An empty distribution is a decode error, so a broken weight list is collected with every other load error
    // instead of aborting the load on its own.
    private static DataResult<WeightedList> decode(List<Weighted<NumberProvider>> distribution) {
        return distribution.isEmpty()
                ? DataResult.error(() -> "Weighted list requires at least one entry")
                : DataResult.success(new WeightedList(distribution));
    }

    @Override
    public double evaluate(FormulaContext context) {
        long total = this.total;
        if (total <= 0L) {
            FormulaDiagnostics.report("Number provider WeightedList has an invalid total weight; using 0");
            return 0.0D;
        }
        Weighted<NumberProvider> entry = Weighted.select(this.distribution, total, context.random());
        return entry == null ? 0.0D : entry.value().evaluate(context);
    }

    @Override
    public MapCodec<WeightedList> codec() {
        return MAP_CODEC;
    }
}
