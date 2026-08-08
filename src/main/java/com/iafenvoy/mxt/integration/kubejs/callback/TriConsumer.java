package com.iafenvoy.mxt.integration.kubejs.callback;

@FunctionalInterface
public interface TriConsumer<A, B, C> {
    void accept(A first, B second, C third);
}
