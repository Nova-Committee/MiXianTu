package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.world.AuraZoneDefinition.Rules;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Fully resolved aura at one position. Consumers must use this instead of raw chunk data.
 */
public record AuraResult(double concentration, double regenPerTick, Map<Identifier, Double> elementAura,
                         List<Identifier> environmentTags, Rules rules, double elementFitBonus,
                         double elementConflictPenalty, Identifier source, SourceKind sourceKind) {
    public enum SourceKind {BIOME, DIMENSION, CUSTOM, FORMATION, CHUNK}

    public boolean suppressCultivate() {
        return this.rules.cultivateSuppress();
    }
}
