package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.ValueModifier.Add;
import com.iafenvoy.mxt.util.formula.ValueModifier.Clamp;
import com.iafenvoy.mxt.util.formula.ValueModifier.MultiplyBase;
import com.iafenvoy.mxt.util.formula.ValueModifier.MultiplyTotal;
import com.iafenvoy.mxt.util.formula.ValueModifier.Set;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.function.Function;

/**
 * Code-owned aggregation rule for values contributed by abilities, physiques and formations.
 */
public sealed interface ValueModifier permits Add, MultiplyBase, MultiplyTotal,
        Clamp, Set {
    Codec<ValueModifier> CODEC = MxtTypeRegistries.VALUE_MODIFIER_TYPE.byNameCodec().dispatch("type", ValueModifier::codec, Function.identity());

    double apply(double base, double current, FormulaContext context);

    MapCodec<? extends ValueModifier> codec();

    static double applyAll(double base, List<ValueModifier> modifiers, FormulaContext context) {
        double value = base;
        for (ValueModifier modifier : modifiers) {
            value = modifier.apply(base, value, context);
            if (!Double.isFinite(value)) throw new IllegalStateException("Value modifier produced a non-finite result");
        }
        return value;
    }

    record Add(NumberProvider amount) implements ValueModifier {
        public static final MapCodec<Add> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(Add::new, Add::amount);

        @Override
        public double apply(double base, double current, FormulaContext context) {
            return current + this.amount.evaluate(context);
        }

        @Override
        public MapCodec<Add> codec() {
            return CODEC;
        }
    }

    record MultiplyBase(NumberProvider factor) implements ValueModifier {
        public static final MapCodec<MultiplyBase> CODEC = NumberProvider.CODEC.fieldOf("factor").xmap(MultiplyBase::new, MultiplyBase::factor);

        @Override
        public double apply(double base, double current, FormulaContext context) {
            return current + base * this.factor.evaluate(context);
        }

        @Override
        public MapCodec<MultiplyBase> codec() {
            return CODEC;
        }
    }

    record MultiplyTotal(NumberProvider factor) implements ValueModifier {
        public static final MapCodec<MultiplyTotal> CODEC = NumberProvider.CODEC.fieldOf("factor").xmap(MultiplyTotal::new, MultiplyTotal::factor);

        @Override
        public double apply(double base, double current, FormulaContext context) {
            return current * (1.0D + this.factor.evaluate(context));
        }

        @Override
        public MapCodec<MultiplyTotal> codec() {
            return CODEC;
        }
    }

    record Clamp(NumberProvider min, NumberProvider max) implements ValueModifier {
        public static final MapCodec<Clamp> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                NumberProvider.CODEC.fieldOf("min").forGetter(Clamp::min), NumberProvider.CODEC.fieldOf("max").forGetter(Clamp::max)
        ).apply(instance, Clamp::new));

        @Override
        public double apply(double base, double current, FormulaContext context) {
            double min = this.min.evaluate(context);
            double max = this.max.evaluate(context);
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
                throw new IllegalStateException("Invalid modifier clamp");
            return Math.clamp(current, min, max);
        }

        @Override
        public MapCodec<Clamp> codec() {
            return CODEC;
        }
    }

    record Set(NumberProvider value) implements ValueModifier {
        public static final MapCodec<Set> CODEC = NumberProvider.CODEC.fieldOf("value").xmap(Set::new, Set::value);

        @Override
        public double apply(double base, double current, FormulaContext context) {
            return this.value.evaluate(context);
        }

        @Override
        public MapCodec<Set> codec() {
            return CODEC;
        }
    }
}
