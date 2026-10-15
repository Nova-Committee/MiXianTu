package com.iafenvoy.mxt.util;

import it.unimi.dsi.fastutil.floats.FloatConsumer;
import it.unimi.dsi.fastutil.floats.FloatPredicate;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;

import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

public final class OptionalFloat {
    private static final OptionalFloat EMPTY = new OptionalFloat(false, 0.0F);
    private final boolean present;
    private final float value;

    private OptionalFloat(boolean present, float value) {
        this.present = present;
        this.value = value;
    }

    public static OptionalFloat empty() {
        return EMPTY;
    }

    // Nothing to intern here, unlike the boolean's two values: every present instance is a new object.
    public static OptionalFloat of(float value) {
        return new OptionalFloat(true, value);
    }

    public boolean isPresent() {
        return this.present;
    }

    public boolean isEmpty() {
        return !this.present;
    }

    public float getAsFloat() {
        if (!this.present)
            throw new NoSuchElementException();
        return this.value;
    }

    public float orElse(float other) {
        return this.present ? this.value : other;
    }

    // Neither the JDK nor fastutil has a FloatSupplier, so the supplier is the double one; the value only widens.
    public float orElseGet(DoubleSupplier other) {
        return this.present ? this.value : (float) other.getAsDouble();
    }

    public <X extends Throwable> float orElseThrow(Supplier<X> exceptionSupplier) throws X {
        if (!this.present)
            throw exceptionSupplier.get();
        return this.value;
    }

    public float orElseThrow() {
        if (!this.present)
            throw new NoSuchElementException();
        return this.value;
    }

    public DoubleStream stream() {
        return this.present ? DoubleStream.of(this.value) : DoubleStream.empty();
    }

    public void ifPresent(FloatConsumer action) {
        if (this.present)
            action.accept(this.value);
    }

    public void ifPresentOrElse(FloatConsumer action, Runnable emptyAction) {
        if (this.present)
            action.accept(this.value);
        else
            emptyAction.run();
    }

    public OptionalFloat filter(FloatPredicate predicate) {
        return this.present && !predicate.test(this.value) ? EMPTY : this;
    }

    public OptionalFloat map(FloatUnaryOperator mapper) {
        return this.present ? new OptionalFloat(true, mapper.apply(this.value)) : EMPTY;
    }

    public OptionalDouble toDouble() {
        return this.present ? OptionalDouble.of(this.value) : OptionalDouble.empty();
    }

    // By value, so a record holding one compares equal to another carrying the same number. Float.compare keeps the
    // distinctions Float.hashCode also keeps: NaN equals NaN, and 0.0f is not -0.0f.
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof OptionalFloat optional
                && this.present == optional.present
                && (!this.present || Float.compare(this.value, optional.value) == 0);
    }

    @Override
    public int hashCode() {
        return !this.present ? 0 : Float.hashCode(this.value);
    }

    @Override
    public String toString() {
        return !this.present ? "empty" : Float.toString(this.value);
    }
}
