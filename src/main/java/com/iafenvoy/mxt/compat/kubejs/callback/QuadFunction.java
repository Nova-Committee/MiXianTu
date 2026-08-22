package com.iafenvoy.mxt.compat.kubejs.callback;

@FunctionalInterface
public interface QuadFunction<A, B, C, D, R> {
    R apply(A first, B second, C third, D fourth);
}
