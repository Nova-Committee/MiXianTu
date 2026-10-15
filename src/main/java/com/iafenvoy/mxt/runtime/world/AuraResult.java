package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.aura.AuraZone.Rules;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * Fully resolved aura at one position; consumers must use this instead of raw chunk data. The key set of
 * {@link #aura} is the whole answer to "which auras is this place made of", so a place can never name an aura
 * it does not hold.
 */
public record AuraResult(Map<Holder<Aura>, AuraPool> aura, Rules rules, double elementFitBonus,
                         double elementConflictPenalty, EntityCondition cultivateCondition, Distribution distribution,
                         Holder<AuraZone> source, SourceKind sourceKind) {
    public AuraResult(Map<Holder<Aura>, AuraPool> aura, AuraService.Resolved resolved) {
        this(aura, resolved.zone().value(), resolved);
    }

    public AuraResult(Map<Holder<Aura>, AuraPool> aura, AuraZone zone, AuraService.Resolved resolved) {
        this(aura, zone, resolved.zone(), resolved.kind());
    }

    public AuraResult(Map<Holder<Aura>, AuraPool> aura, AuraZone zone, Holder<AuraZone> source, SourceKind sourceKind) {
        this(aura, zone.rules(), zone.elementFitBonus(), zone.elementConflictPenalty(), zone.cultivateCondition(), zone.distribution(), source, sourceKind);
    }

    public AuraResult copy(Map<Holder<Aura>, AuraPool> aura) {
        return new AuraResult(aura, this.rules, this.elementFitBonus, this.elementConflictPenalty, this.cultivateCondition, this.distribution, this.source, this.sourceKind);
    }

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

    public AuraPool pool(Holder<Aura> aura) {
        return this.aura.getOrDefault(aura, AuraPool.empty());
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
