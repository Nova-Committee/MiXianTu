package com.iafenvoy.mxt.runtime.resulting;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public record FailureSource(Identifier id) {
    public static FailureSource of(ResourceKey<? extends Registry<?>> registry) {
        return new FailureSource(registry.identifier());
    }
}
