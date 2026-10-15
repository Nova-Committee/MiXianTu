package com.iafenvoy.mxt.util;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.booleans.BooleanPredicate;
import it.unimi.dsi.fastutil.booleans.BooleanUnaryOperator;

import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class OptionalBoolean {
    private static final OptionalBoolean EMPTY = new OptionalBoolean(false, false);
    private static final OptionalBoolean OPTIONAL_FALSE = new OptionalBoolean(true, false);
    private static final OptionalBoolean OPTIONAL_TRUE = new OptionalBoolean(true, true);
    private final boolean present;
    private final boolean value;

    private OptionalBoolean(boolean present, boolean value) {
        this.present = present;
        this.value = value;
    }

    public static OptionalBoolean empty() {
        return EMPTY;
    }

    public static OptionalBoolean of(boolean value) {
        return value ? OPTIONAL_TRUE : OPTIONAL_FALSE;
    }

    public boolean isPresent() {
        return this.present;
    }

    public boolean isEmpty() {
        return !this.present;
    }

    public boolean getAsBoolean() {
        if (!this.present)
            throw new NoSuchElementException();
        return this.value;
    }

    public boolean orElse(boolean other) {
        return this.present ? this.value : other;
    }

    public boolean orElseGet(BooleanSupplier other) {
        return this.present ? this.value : other.getAsBoolean();
    }

    public <X extends Throwable> boolean orElseThrow(Supplier<X> exceptionSupplier) throws X {
        if (!this.present)
            throw exceptionSupplier.get();
        return this.value;
    }

    public boolean orElseThrow() {
        if (!this.present)
            throw new NoSuchElementException();
        return this.value;
    }

    public IntStream stream() {
        return this.present ? IntStream.of(this.value ? 1 : 0) : IntStream.empty();
    }

    public void ifPresent(BooleanConsumer action) {
        if (this.present)
            action.accept(this.value);
    }

    public void ifPresentOrElse(BooleanConsumer action, Runnable emptyAction) {
        if (this.present)
            action.accept(this.value);
        else
            emptyAction.run();
    }

    public OptionalBoolean filter(BooleanPredicate predicate) {
        return this.present && !predicate.test(this.value) ? EMPTY : this;
    }

    public OptionalBoolean map(BooleanUnaryOperator mapper) {
        return this.present ? of(mapper.apply(this.value)) : EMPTY;
    }

    public OptionalInt toInt() {
        if (this.present)
            return OptionalInt.of(this.value ? 1 : 0);
        else
            return OptionalInt.empty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof OptionalBoolean optional && this.present == optional.present
                && (!this.present || this.value == optional.value);
    }

    @Override
    public int hashCode() {
        return !this.present ? 0 : Boolean.hashCode(this.value);
    }

    @Override
    public String toString() {
        return !this.present ? "empty" : this.value ? "true" : "false";
    }
}
