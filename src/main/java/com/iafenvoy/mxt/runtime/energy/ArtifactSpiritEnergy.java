package com.iafenvoy.mxt.runtime.energy;

import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * ISpiritEnergy adapter for an ItemStack's persisted artifact state.
 */
public final class ArtifactSpiritEnergy implements ISpiritEnergy {
    private final ItemStack stack;
    private final double capacity;

    public ArtifactSpiritEnergy(@NotNull ItemStack stack, double capacity) {
        this.stack = stack;
        if (!Double.isFinite(capacity) || capacity < 0.0D)
            throw new IllegalArgumentException("Artifact capacity must be finite and non-negative");
        this.capacity = capacity;
    }

    @Override
    public double energy() {
        return ArtifactService.state(this.stack).spiritEnergy();
    }

    @Override
    public double capacity() {
        return this.capacity;
    }

    @Override
    public void setEnergy(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Artifact energy must be finite");
        ArtifactService.setEnergy(this.stack, Math.max(0.0D, Math.min(this.capacity, value)));
    }
}
