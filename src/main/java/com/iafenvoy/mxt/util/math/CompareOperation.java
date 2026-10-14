package com.iafenvoy.mxt.util.math;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BiPredicate;

public enum CompareOperation implements StringRepresentable {
    LESS_THAN("<", (a, b) -> a < b),
    LESS_THAN_OR_EQUAL("<=", (a, b) -> a <= b),
    GREATER_THAN(">", (a, b) -> a > b),
    GREATER_THAN_OR_EQUAL(">=", (a, b) -> a >= b),
    EQUAL("==", Objects::equals),
    NOT_EQUAL("!=", (a, b) -> !Objects.equals(a, b));
    public static final Codec<CompareOperation> CODEC = StringRepresentable.fromEnum(CompareOperation::values);
    private final String name;
    private final BiPredicate<Double, Double> predicate;

    CompareOperation(String name, BiPredicate<Double, Double> predicate) {
        this.name = name;
        this.predicate = predicate;
    }

    public boolean compare(double value, double compareTo) {
        return this.predicate.test(value, compareTo);
    }

    /**
     * Ranks are indices, so they are compared as whole numbers: no double rounding stands between two positions.
     */
    public boolean compare(int value, int compareTo) {
        return this.predicate.test((double) value, (double) compareTo);
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }
}
