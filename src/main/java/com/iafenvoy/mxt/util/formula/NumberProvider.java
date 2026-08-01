package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.util.formula.NumberProvider.ContextVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider.Expression;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A number that can be a JSON constant or an exp4j expression string.
 */
public sealed interface NumberProvider permits Constant, Expression, ContextVariable {
    Codec<NumberProvider> TYPED_CODEC = MxtTypeRegistries.NUMBER_PROVIDER_TYPE.byNameCodec().dispatch("type", NumberProvider::codec, Function.identity());
    Codec<NumberProvider> CODEC = Codec.either(Codec.DOUBLE, Codec.either(Codec.STRING, TYPED_CODEC)).comapFlatMap(
            value -> value.map(
                    constant -> DataResult.success(new Constant(constant)),
                    rest -> rest.map(
                            expression -> Expression.create(expression).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid number expression: " + expression)),
                            DataResult::success
                    )
            ),
            provider -> provider instanceof Constant(double value)
                    ? Either.left(value)
                    : provider instanceof Expression expression
                    ? Either.right(Either.left(expression.source()))
                    : Either.right(Either.right(provider))
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
}
