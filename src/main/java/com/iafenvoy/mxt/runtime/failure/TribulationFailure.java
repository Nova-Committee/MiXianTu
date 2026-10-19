package com.iafenvoy.mxt.runtime.failure;

public final class TribulationFailure {
    public static final Failure EMPTY_TIMELINE = SimpleFailure.of("empty_timeline", FailureSources.TRIBULATION);
    public static final Failure INVALID_ENTRY = SimpleFailure.of("invalid_entry", FailureSources.TRIBULATION);

    private TribulationFailure() {
    }
}
