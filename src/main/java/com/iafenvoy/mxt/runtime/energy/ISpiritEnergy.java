package com.iafenvoy.mxt.runtime.energy;

/**
 * Common server-authoritative spirit-energy contract for artifacts, formations and world storage.
 */
public interface ISpiritEnergy {
    double energy();

    double capacity();

    void setEnergy(double value);

    default double receive(double amount) {
        validate(amount);
        double accepted = Math.min(amount, Math.max(0.0D, this.capacity() - this.energy()));
        if (accepted > 0.0D) this.setEnergy(this.energy() + accepted);
        return accepted;
    }

    default double extract(double amount) {
        validate(amount);
        double extracted = Math.min(amount, Math.max(0.0D, this.energy()));
        if (extracted > 0.0D) this.setEnergy(this.energy() - extracted);
        return extracted;
    }

    private static void validate(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D)
            throw new IllegalArgumentException("Spirit energy amount must be finite and non-negative");
    }
}
