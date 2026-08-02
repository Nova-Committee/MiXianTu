package com.iafenvoy.mxt.runtime.behavior;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Resolves a direct behaviour ID and prevents an extension failure from breaking its transaction owner.
 */
public final class DomainBehaviorService {
    private DomainBehaviorService() {
    }

    /**
     * Returns false when an explicitly requested built-in behaviour is unavailable or throws.
     */
    public static boolean execute(Registry<DomainBehavior> registry, Optional<Identifier> id, BehaviorContext context) {
        if (id.isEmpty()) return true;
        DomainBehavior behavior = registry.get(id.get()).map(Reference::value).orElse(null);
        if (behavior == null) {
            MiXianTu.LOGGER.warn("Missing domain behavior {} for {}", id.get(), context.definition());
            return false;
        }
        try {
            behavior.execute(context);
            return true;
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Domain behavior {} failed for {}", id.get(), context.definition(), exception);
            return false;
        }
    }
}
