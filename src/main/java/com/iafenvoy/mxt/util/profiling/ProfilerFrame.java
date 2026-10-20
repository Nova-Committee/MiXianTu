package com.iafenvoy.mxt.util.profiling;

public record ProfilerFrame(long sequence, boolean enabled, ProfilerWindowSnapshot window) {
    public static final ProfilerFrame EMPTY = new ProfilerFrame(0L, false, ProfilerWindowSnapshot.EMPTY);
}
