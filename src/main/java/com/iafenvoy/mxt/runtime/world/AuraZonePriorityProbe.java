package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Development-only probe for the aura zone ordering rule. It lives in this package so it can drive
 * the same helper the runtime uses instead of re-implementing the rule.
 */
public final class AuraZonePriorityProbe {
    private AuraZonePriorityProbe() {
    }

    /**
     * Selects a winner through the production helper and reports its ID.
     */
    public static Optional<Identifier> select(List<Reference<AuraZone>> candidates) {
        return AuraService.pickHighestPriority(candidates.stream()).map(HolderHelper::id);
    }
}
