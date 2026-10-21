package com.iafenvoy.mxt.util.dispatcher;

// A zero spread selects a fixed limit; otherwise the population is spread over that many ticks.
public record TickExecutionLimit(boolean enabled, int minimum, int spreadTicks) {
    public TickExecutionLimit {
        if (minimum <= 0 || spreadTicks < 0)
            throw new IllegalArgumentException("An execution limit needs a positive minimum and a non-negative spread");
    }

    public static TickExecutionLimit unlimited() {
        return new TickExecutionLimit(false, Integer.MAX_VALUE, 0);
    }

    public static TickExecutionLimit fixed(int maximum) {
        return new TickExecutionLimit(true, maximum, 0);
    }

    public static TickExecutionLimit adaptive(int minimum, int spreadTicks) {
        if (spreadTicks <= 0) throw new IllegalArgumentException("An adaptive limit needs a positive spread");
        return new TickExecutionLimit(true, minimum, spreadTicks);
    }

    public TickExecutionLimit withEnabled(boolean enabled) {
        return new TickExecutionLimit(enabled, this.minimum, this.spreadTicks);
    }

    public int maximum(int scheduledTasks) {
        if (scheduledTasks < 0) throw new IllegalArgumentException("Task count cannot be negative");
        if (!this.enabled) return Integer.MAX_VALUE;
        if (this.spreadTicks == 0) return this.minimum;
        int shared = scheduledTasks / this.spreadTicks + (scheduledTasks % this.spreadTicks == 0 ? 0 : 1);
        return Math.max(this.minimum, shared);
    }
}
