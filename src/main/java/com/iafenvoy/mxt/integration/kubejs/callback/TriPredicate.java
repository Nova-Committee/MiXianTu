package com.iafenvoy.mxt.integration.kubejs.callback;

@FunctionalInterface
public interface TriPredicate<A, B, C> {
    boolean test(A first, B second, C third);
}
