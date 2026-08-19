package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.Trio;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.Expression;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.function.Function;

/**
 * A number that can be a JSON constant, an exp4j expression, or one of the
 * built-in structured providers.
 *
 */
public interface NumberProvider {
    Logger LOGGER = LogUtils.getLogger();
    Codec<Double> FINITE_DOUBLE_CODEC = Codec.DOUBLE.validate(value -> Double.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Number provider value must be finite: " + value));
    Codec<NumberProvider> TYPED_CODEC = MxtRegistries.NUMBER_PROVIDER_TYPE.byNameCodec().dispatch("type", NumberProvider::codec, Function.identity());
    Codec<NumberProvider> CODEC = Trio.codec(Codec.DOUBLE, Codec.STRING, TYPED_CODEC).comapFlatMap(
            value -> value.map(
                    constant -> Double.isFinite(constant) ? DataResult.success(new Constant(constant)) : DataResult.error(() -> "Number provider value must be finite: " + constant),
                    expression -> Expression.create(expression).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid number expression: " + expression)),
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
        LOGGER.warn("Number provider {} produced non-finite value {}; using 0", this.getClass().getSimpleName(), value);
        return false;
    }
}
