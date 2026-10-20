package com.iafenvoy.mxt.util.function;

@FunctionalInterface
public interface TriPredicate<A, B, C> {
    boolean test(A first, B second, C third);
}
