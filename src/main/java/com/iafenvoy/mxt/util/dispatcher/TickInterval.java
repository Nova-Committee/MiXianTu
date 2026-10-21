package com.iafenvoy.mxt.util.dispatcher;

import org.jetbrains.annotations.NotNull;

import java.util.random.RandomGenerator;

// Both endpoints are inclusive; positive delays prevent a recurring task from executing twice in one tick.
public record TickInterval(int minTicks, int maxTicks) {
    public TickInterval {
        if (minTicks <= 0 || maxTicks < minTicks)
            throw new IllegalArgumentException("Tick intervals must satisfy 0 < min <= max");
    }

    public static TickInterval fixed(int ticks) {
        return new TickInterval(ticks, ticks);
    }

    public static TickInterval random(int minTicks, int maxTicks) {
        return new TickInterval(minTicks, maxTicks);
    }

    public long nextDelay(@NotNull RandomGenerator random) {
        return this.minTicks == this.maxTicks ? this.minTicks : random.nextLong(this.minTicks, (long) this.maxTicks + 1L);
    }
}
