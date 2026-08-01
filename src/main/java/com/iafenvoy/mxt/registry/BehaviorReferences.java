package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.runtime.behavior.DomainBehavior;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Shared reload-time validation for direct references into code-owned behaviour registries.
 */
public final class BehaviorReferences {
    private BehaviorReferences() {
    }

    public static <T> DataResult<T> validate(T value, Registry<DomainBehavior> registry, Reference... references) {
        for (Reference reference : references) {
            Optional<Identifier> id = reference.id();
            if (id.isPresent() && registry.get(id.orElseThrow()).isEmpty()) {
                return DataResult.error(() -> "Unknown " + reference.field() + " '" + id.orElseThrow() + "' in " + registry.key().identifier());
            }
        }
        return DataResult.success(value);
    }

    public record Reference(String field, Optional<Identifier> id) {
    }
}
