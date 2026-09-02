package com.iafenvoy.mxt.data.resourcebar;

/**
 * Client-facing immutable input for visibility policies. Rendering itself is intentionally outside the core module.
 */
public record ResourceBarView(double current, double minimum, double maximum, long ticksSinceChanged,
                              boolean hasAbility) {
    public ResourceBarView {
        if (!Double.isFinite(current) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || maximum < 0.0D || ticksSinceChanged < 0L)
            throw new IllegalArgumentException("Invalid resource bar view");
    }
}
