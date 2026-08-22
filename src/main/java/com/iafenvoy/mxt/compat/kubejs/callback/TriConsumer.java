package com.iafenvoy.mxt.compat.kubejs.callback;

@FunctionalInterface
public interface TriConsumer<A, B, C> {
    void accept(A first, B second, C third);
}
