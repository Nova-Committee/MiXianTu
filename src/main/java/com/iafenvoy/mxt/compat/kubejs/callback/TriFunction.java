package com.iafenvoy.mxt.compat.kubejs.callback;

@FunctionalInterface
public interface TriFunction<A, B, C, R> {
    R apply(A first, B second, C third);
}
