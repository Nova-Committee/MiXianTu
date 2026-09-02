package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.aura.AuraZone.Rules;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.resource.Resource;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Fully resolved aura at one position. Consumers must use this instead of raw chunk data.
 */
public record AuraResult(Map<Holder<Resource>, AuraPool> aura,
                         List<Identifier> auraKinds, Rules rules, double elementFitBonus,
                         double elementConflictPenalty, EntityCondition cultivateCondition, Distribution distribution,
                         Identifier source, SourceKind sourceKind) {
    public enum SourceKind {BIOME, DIMENSION, CUSTOM, FORMATION, CHUNK}

    public double concentration() {
        return this.aura.values().stream().mapToDouble(AuraPool::amount).sum();
    }

    public double maximum() {
        return this.aura.values().stream().mapToDouble(AuraPool::maximum).sum();
    }

    public double regenPerTick() {
        return this.aura.values().stream().mapToDouble(AuraPool::regenPerTick).sum();
    }

    public AuraPool pool(Holder<Resource> resource) {
        return this.aura.getOrDefault(resource, new AuraPool(0.0D, 0.0D, 0.0D));
    }

    public boolean suppressCultivate() {
        return this.rules.cultivateSuppress();
    }

    /**
     * Relative concentration controls the base cultivation speed for this position.
     */
    public double cultivationSpeed() {
        double maximum = this.maximum();
        double concentration = this.concentration();
        if (!Double.isFinite(maximum)) return concentration / (concentration + 1.0D);
        return Math.clamp(concentration / Math.max(1.0D, maximum), 0.0D, 1.0D);
    }

}
