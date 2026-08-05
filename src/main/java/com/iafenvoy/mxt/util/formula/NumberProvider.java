package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.Trio;
import com.iafenvoy.mxt.util.formula.NumberProvider.Binomial;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.util.formula.NumberProvider.ContextVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider.Expression;
import com.iafenvoy.mxt.util.formula.NumberProvider.Sum;
import com.iafenvoy.mxt.util.formula.NumberProvider.Uniform;
import com.iafenvoy.mxt.util.formula.NumberProvider.WeightedList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A number that can be a JSON constant, an exp4j expression, or one of the
 * built-in structured providers.
 *
 */
public sealed interface NumberProvider permits Binomial, Constant, ContextVariable, Expression, Sum, Uniform, WeightedList {
    Codec<NumberProvider> TYPED_CODEC = MxtTypeRegistries.NUMBER_PROVIDER_TYPE.byNameCodec().dispatch("type", NumberProvider::codec, Function.identity());
    Codec<NumberProvider> CODEC = Trio.codec(Codec.DOUBLE, Codec.STRING, TYPED_CODEC).comapFlatMap(
            value -> value.map(
                    constant -> DataResult.success(new Constant(constant)),
                    expression -> Expression.create(expression).map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Invalid number expression: " + expression)),
                    DataResult::success
            ),
            Trio::third
    );

    double evaluate(FormulaContext context);

    MapCodec<? extends NumberProvider> codec();

    record Constant(double value) implements NumberProvider {
        public static final MapCodec<Constant> MAP_CODEC = Codec.DOUBLE.fieldOf("value").xmap(Constant::new, Constant::value);

        public Constant {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Constant value must be finite");
            }
        }

        @Override
        public double evaluate(FormulaContext context) {
            return this.value;
        }

        @Override
        public MapCodec<Constant> codec() {
            return MAP_CODEC;
        }
    }

    final class Expression implements NumberProvider {
        public static final MapCodec<Expression> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("expression").forGetter(Expression::source)
        ).apply(instance, Expression::new));

        private final String source;
        private final Set<String> variables;
        private final ThreadLocal<net.objecthunter.exp4j.Expression> compiled;

        public Expression(@NotNull String source) {
            this.source = source.trim();
            if (this.source.isEmpty()) {
                throw new IllegalArgumentException("Expression must not be empty");
            }
            this.variables = FormulaVariables.find(this.source);
            this.compiled = ThreadLocal.withInitial(() -> new ExpressionBuilder(this.source)
                    .functions(FormulaFunctions.all())
                    .variables(this.variables)
                    .build());
        }

        static Optional<Expression> create(String source) {
            try {
                return Optional.of(new Expression(source));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        public String source() {
            return this.source;
        }

        @Override
        public double evaluate(FormulaContext context) {
            net.objecthunter.exp4j.Expression expression = this.compiled.get();
            for (String variable : this.variables) {
                expression.setVariable(variable, context.value(variable));
            }
            double result = expression.evaluate();
            if (!Double.isFinite(result)) {
                throw new IllegalStateException("Formula produced a non-finite result: " + this.source);
            }
            return result;
        }

        @Override
        public MapCodec<Expression> codec() {
            return MAP_CODEC;
        }
    }

    /**
     * Resolves a declared variable directly, with a finite fallback for absent context values.
     */
    record ContextVariable(String variable, double fallback) implements NumberProvider {
        public static final MapCodec<ContextVariable> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("variable").forGetter(ContextVariable::variable),
                Codec.DOUBLE.optionalFieldOf("fallback", 0.0D).forGetter(ContextVariable::fallback)
        ).apply(instance, ContextVariable::new));

        public ContextVariable {
            if (variable.isBlank() || !Double.isFinite(fallback)) {
                throw new IllegalArgumentException("Invalid context variable provider");
            }
        }

        @Override
        public double evaluate(FormulaContext context) {
            return context.contains(this.variable) ? context.value(this.variable) : this.fallback;
        }

        @Override
        public MapCodec<ContextVariable> codec() {
            return MAP_CODEC;
        }
    }

    record Sum(List<NumberProvider> summands) implements NumberProvider {
        public static final MapCodec<Sum> MAP_CODEC = NumberProvider.CODEC.listOf().fieldOf("summands").xmap(Sum::new, Sum::summands);

        public Sum {
            summands = List.copyOf(summands);
            if (summands.isEmpty()) throw new IllegalArgumentException("Sum requires at least one summand");
        }

        @Override
        public double evaluate(FormulaContext context) {
            double result = 0.0D;
            for (NumberProvider summand : this.summands) result += summand.evaluate(context);
            if (!Double.isFinite(result)) throw new IllegalStateException("Sum produced a non-finite result");
            return result;
        }

        @Override
        public MapCodec<Sum> codec() {
            return MAP_CODEC;
        }
    }

    record Uniform(NumberProvider min, NumberProvider max) implements NumberProvider {
        public static final MapCodec<Uniform> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                NumberProvider.CODEC.fieldOf("min").forGetter(Uniform::min),
                NumberProvider.CODEC.fieldOf("max").forGetter(Uniform::max)
        ).apply(instance, Uniform::new));

        @Override
        public double evaluate(FormulaContext context) {
            double lower = this.min.evaluate(context);
            double upper = this.max.evaluate(context);
            if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper)
                throw new IllegalStateException("Invalid uniform range");
            return lower == upper ? lower : lower + context.random().nextDouble() * (upper - lower);
        }

        @Override
        public MapCodec<Uniform> codec() {
            return MAP_CODEC;
        }
    }

    record Binomial(NumberProvider trials, NumberProvider probability) implements NumberProvider {
        private static final int MAX_TRIALS = 16_384;
        public static final MapCodec<Binomial> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                NumberProvider.CODEC.fieldOf("n").forGetter(Binomial::trials),
                NumberProvider.CODEC.fieldOf("p").forGetter(Binomial::probability)
        ).apply(instance, Binomial::new));

        @Override
        public double evaluate(FormulaContext context) {
            double rawTrials = this.trials.evaluate(context);
            double chance = this.probability.evaluate(context);
            if (!Double.isFinite(rawTrials) || rawTrials < 0.0D || rawTrials > MAX_TRIALS || Math.rint(rawTrials) != rawTrials
                    || !Double.isFinite(chance) || chance < 0.0D || chance > 1.0D)
                throw new IllegalStateException("Invalid binomial parameters");
            int result = 0;
            for (int index = 0; index < (int) rawTrials; index++) if (context.random().nextDouble() < chance) result++;
            return result;
        }

        @Override
        public MapCodec<Binomial> codec() {
            return MAP_CODEC;
        }
    }

    record WeightedList(List<Entry> distribution) implements NumberProvider {
        public static final MapCodec<WeightedList> MAP_CODEC = Entry.MAP_CODEC.codec().listOf().fieldOf("distribution").xmap(WeightedList::new, WeightedList::distribution);

        public WeightedList {
            distribution = List.copyOf(distribution);
            if (distribution.isEmpty()) throw new IllegalArgumentException("Weighted list requires at least one entry");
        }

        @Override
        public double evaluate(FormulaContext context) {
            long total = 0L;
            for (Entry entry : this.distribution) total = Math.addExact(total, entry.weight());
            if (total <= 0L) throw new IllegalStateException("Weighted list has no positive weight");
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
                    NumberProvider.CODEC.fieldOf("data").forGetter(Entry::data),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(Entry::weight)
            ).apply(instance, Entry::new));
        }
    }

}
