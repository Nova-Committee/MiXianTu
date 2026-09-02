package com.iafenvoy.mxt.util.math;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.DoubleBinaryOperator;

/**
 * Origins-style numeric modifier. Values remain formula providers so datapack
 * content can still scale them from the current formula context.
 */
public record Modifier(NumberProvider value, ModifierOperation operation) {
    public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
            NumberProvider.CODEC.fieldOf("value").forGetter(Modifier::value),
            ModifierOperation.CODEC.optionalFieldOf("operation", ModifierOperation.ADD_BASE_EARLY).forGetter(Modifier::operation)
    ).apply(i, Modifier::new));

    public double value(FormulaContext context) {
        double resolved = this.value.evaluate(context);
        if (!Double.isFinite(resolved)) throw new IllegalStateException("Modifier value must be finite");
        return resolved;
    }

    public static double applyModifiers(FormulaContext context, List<Modifier> modifiers, double initialValue) {
        Map<ModifierOperation, List<Double>> grouped = new EnumMap<>(ModifierOperation.class);
        for (Modifier modifier : modifiers) {
            grouped.computeIfAbsent(modifier.operation, ignored -> new ArrayList<>()).add(modifier.value(context));
        }
        double result = initialValue;
        for (ModifierOperation operation : ModifierOperation.values()) {
            List<Double> values = grouped.get(operation);
            if (values != null) result = operation.apply(result, values);
        }
        if (!Double.isFinite(result)) throw new IllegalStateException("Modifier result must be finite");
        return result;
    }

    public enum ModifierOperation implements StringRepresentable {
        ADD_BASE_EARLY(false, Double::sum),
        MULTIPLY_BASE_ADDITIVE(true, (current, value) -> current * (1.0D + value)),
        MULTIPLY_BASE_MULTIPLICATIVE(false, (current, value) -> current * (1.0D + value)),
        ADD_BASE_LATE(false, Double::sum),
        MULTIPLY_TOTAL_ADDITIVE(true, (current, value) -> current * (1.0D + value)),
        MULTIPLY_TOTAL_MULTIPLICATIVE(false, (current, value) -> current * (1.0D + value)),
        SET_TOTAL(false, (current, value) -> value),
        MIN_TOTAL(false, Math::min),
        MAX_TOTAL(false, Math::max);
        public static final Codec<ModifierOperation> CODEC = StringRepresentable.fromEnum(ModifierOperation::values);
        private final boolean aggregate;
        private final DoubleBinaryOperator operator;

        ModifierOperation(boolean aggregate, DoubleBinaryOperator operator) {
            this.aggregate = aggregate;
            this.operator = operator;
        }

        private double apply(double current, List<Double> values) {
            if (this.aggregate)
                return this.operator.applyAsDouble(current, values.stream().mapToDouble(Double::doubleValue).sum());
            for (double value : values) current = this.operator.applyAsDouble(current, value);
            return current;
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
