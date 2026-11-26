package com.iafenvoy.mxt.registry;

import net.minecraft.core.Holder.Reference;

import java.util.Comparator;
import java.util.function.ToIntFunction;

public final class RegistryPrecedence {
    private RegistryPrecedence() {
    }

    public static <T> Comparator<Reference<T>> order(ToIntFunction<T> priority) {
        return Comparator.<Reference<T>>comparingInt(entry -> priority.applyAsInt(entry.value()))
                .reversed().thenComparing(entry -> entry.key().identifier());
    }
}
