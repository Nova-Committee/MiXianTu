package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Binomial(NumberProvider trials, NumberProvider probability) implements NumberProvider {
    private static final int MAX_TRIALS = 16_384;
    public static final MapCodec<Binomial> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CODEC.fieldOf("n").forGetter(Binomial::trials),
            CODEC.fieldOf("p").forGetter(Binomial::probability)
    ).apply(i, Binomial::new));

    @Override
    public double evaluate(FormulaContext context) {
        double rawTrials = this.trials.evaluate(context);
        double chance = this.probability.evaluate(context);
        if (rawTrials < 0.0D || rawTrials > MAX_TRIALS || Math.rint(rawTrials) != rawTrials
                || chance < 0.0D || chance > 1.0D) {
            LOGGER.warn("Number provider Binomial received invalid parameters n={}, p={}; using 0", rawTrials, chance);
            return 0.0D;
        }
        int result = 0;
        for (int index = 0; index < (int) rawTrials; index++) if (context.random().nextDouble() < chance) result++;
        return result;
    }

    @Override
    public MapCodec<Binomial> codec() {
        return MAP_CODEC;
    }
}
