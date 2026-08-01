package com.iafenvoy.mxt.runtime.energy;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import org.jetbrains.annotations.NotNull;


/**
 * ISpiritEnergy adapter for a chunk's aura concentration.
 */
public final class AuraSpiritEnergy implements ISpiritEnergy {
    private final AuraChunkData aura;
    private final double capacity;

    public AuraSpiritEnergy(@NotNull AuraChunkData aura, double capacity) {
        this.aura = aura;
        if (!Double.isFinite(capacity) || capacity < 0.0D)
            throw new IllegalArgumentException("Aura capacity must be finite and non-negative");
        this.capacity = capacity;
    }

    @Override
    public double energy() {
        return this.aura.concentration();
    }

    @Override
    public double capacity() {
        return this.capacity;
    }

    @Override
    public void setEnergy(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Aura energy must be finite");
        this.aura.setConcentration(Math.max(0.0D, Math.min(this.capacity, value)));
    }
}
