package com.iafenvoy.mxt.compat.kubejs.callback;

@FunctionalInterface
public interface QuadPredicate<A, B, C, D> {
    boolean test(A first, B second, C third, D fourth);
}
