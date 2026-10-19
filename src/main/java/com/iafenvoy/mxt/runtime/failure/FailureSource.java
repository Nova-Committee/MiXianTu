package com.iafenvoy.mxt.runtime.failure;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Where a refusal comes from: the datapack registry whose definitions can produce it, or {@link #COMMON} when
 * every module can.
 */
public record FailureSource(Identifier id) {
    public static FailureSource of(ResourceKey<? extends Registry<?>> registry) {
        return new FailureSource(registry.identifier());
    }
}
