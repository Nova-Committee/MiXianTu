package com.iafenvoy.mxt.integration.kubejs.callback;

@FunctionalInterface
public interface QuadFunction<A, B, C, D, R> {
    R apply(A first, B second, C third, D fourth);
}
