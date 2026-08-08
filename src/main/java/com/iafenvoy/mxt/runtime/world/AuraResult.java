package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.AuraZone.Rules;
import com.iafenvoy.mxt.data.cultivation.Element;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Fully resolved aura at one position. Consumers must use this instead of raw chunk data.
 */
public record AuraResult(double concentration, double regenPerTick, Map<Holder<Element>, Double> elementAura,
                         List<Identifier> auraKinds, Rules rules, double elementFitBonus,
                         double elementConflictPenalty, Identifier source, SourceKind sourceKind) {
    public enum SourceKind {BIOME, DIMENSION, CUSTOM, FORMATION, CHUNK}

    public boolean suppressCultivate() {
        return this.rules.cultivateSuppress();
    }
}
