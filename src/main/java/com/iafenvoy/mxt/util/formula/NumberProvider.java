package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.Expression;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * A number that can be a JSON constant, an exp4j expression, or one of the built-in structured providers.
 */
public interface NumberProvider {
    Codec<Double> FINITE_DOUBLE_CODEC = Codec.DOUBLE.validate(value -> Double.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Number provider value must be finite: " + value));
    Codec<NumberProvider> TYPED_CODEC = MxtRegistries.NUMBER_PROVIDER_TYPE.byNameCodec().dispatch("type", NumberProvider::codec, Function.identity());
    Codec<NumberProvider> SHORTCUT_CODEC = Codec.either(Codec.DOUBLE, Codec.STRING).xmap(
            e -> e.map(Constant::new, Expression::new),
            entry -> switch (entry) {
                case Constant constant -> Either.left(constant.value());
                case Expression expression -> Either.right(expression.source());
                default ->
                        throw new IllegalArgumentException("Only constants and expressions support shorthand encoding");
            });
    Codec<NumberProvider> CODEC = Codec.either(SHORTCUT_CODEC, TYPED_CODEC).xmap(e -> e.map(Function.identity(), Function.identity()), Either::right);

    double evaluate(FormulaContext context);

    MapCodec<? extends NumberProvider> codec();

    // Verifies a runtime result before it is exposed to game logic; callers must return a suitable fallback when
    // this returns {@code false}.
    default boolean assertFinite(double value) {
        if (Double.isFinite(value)) return true;
        FormulaDiagnostics.report("Number provider " + this.getClass().getSimpleName() + " produced the non-finite value " + value + "; using 0");
        return false;
    }
}
