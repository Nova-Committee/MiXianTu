package com.iafenvoy.mxt.compat.kubejs.callback;

@FunctionalInterface
public interface QuadConsumer<A, B, C, D> {
    void accept(A first, B second, C third, D fourth);
}
