package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.Trio;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.Expression;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * A number that can be a JSON constant, an exp4j expression, or one of the
 * built-in structured providers.
 *
 */
public interface NumberProvider {
    Codec<Double> FINITE_DOUBLE_CODEC = Codec.DOUBLE.validate(value -> Double.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Number provider value must be finite: " + value));
    Codec<NumberProvider> TYPED_CODEC = MxtRegistries.NUMBER_PROVIDER_TYPE.byNameCodec().dispatch("type", NumberProvider::codec, Function.identity());
    Codec<NumberProvider> CODEC = Trio.codec(Codec.DOUBLE, Codec.STRING, TYPED_CODEC).comapFlatMap(
            value -> value.map(
                    constant -> Double.isFinite(constant) ? DataResult.success(new Constant(constant)) : DataResult.error(() -> "Number provider value must be finite: " + constant),
                    Expression::decode,
                    DataResult::success
            ), Trio::third);

    double evaluate(FormulaContext context);

    MapCodec<? extends NumberProvider> codec();

    /**
     * Verifies a runtime result before it is exposed to game logic.
     * Callers must return a suitable fallback when this method returns {@code false}.
     */
    default boolean assertFinite(double value) {
        if (Double.isFinite(value)) return true;
        FormulaDiagnostics.report("Number provider " + this.getClass().getSimpleName()
                + " produced the non-finite value " + value + "; using 0");
        return false;
    }
}
