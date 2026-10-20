package com.iafenvoy.mxt.util.function;

@FunctionalInterface
public interface QuadConsumer<A, B, C, D> {
    void accept(A first, B second, C third, D fourth);
}
