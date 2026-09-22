package com.iafenvoy.mxt.screen.wheel;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * How the wheel writes a length of time: one format for every duration it shows, always one decimal so the
 * number keeps its width while it counts down.
 */
public final class WheelDuration {
    private static final double TICKS_PER_SECOND = 20.0D;

    private WheelDuration() {
    }

    // A non-finite value is written as zero rather than as NaN seconds.
    public static Component seconds(double ticks) {
        double seconds = Double.isFinite(ticks) ? ticks / TICKS_PER_SECOND : 0.0D;
        return Component.translatable("wheel.mxt.tooltip.seconds",
                Component.literal(String.format(Locale.ROOT, "%.1f", seconds)));
    }
}
