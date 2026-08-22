package com.iafenvoy.mxt.compat.kubejs.callback;

@FunctionalInterface
public interface TriPredicate<A, B, C> {
    boolean test(A first, B second, C third);
}
