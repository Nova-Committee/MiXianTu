package com.iafenvoy.mxt.runtime.energy;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * ISpiritEnergy view of one aura an artifact holds; the capacity stays the caller's, so the definition decides
 * it once in {@link ArtifactService}. Amounts are whole units in the shared spirit store, so a write floors.
 */
public final class ArtifactSpiritEnergy implements ISpiritEnergy {
    private final ItemStack stack;
    private final Holder<Aura> aura;
    private final double capacity;

    public ArtifactSpiritEnergy(@NotNull ItemStack stack, @NotNull Holder<Aura> aura, double capacity) {
        this.stack = stack;
        this.aura = aura;
        if (!Double.isFinite(capacity) || capacity < 0.0D)
            throw new IllegalArgumentException("Artifact capacity must be finite and non-negative");
        this.capacity = capacity;
    }

    @Override
    public double energy() {
        return ArtifactService.stored(this.stack, this.aura);
    }

    @Override
    public double capacity() {
        return this.capacity;
    }

    @Override
    public void setEnergy(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Artifact energy must be finite");
        ArtifactService.setEnergy(this.stack, this.aura, (int) Math.floor(Math.max(0.0D, Math.min(this.capacity, value))));
    }
}
