package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

// UNKNOWN_LEVEL belongs to the script entry, which takes ids rather than parsed arguments.
public final class ProgressionFailure {
    public static final Failure UNKNOWN_OWNER = SimpleFailure.of("unknown_owner", FailureSources.PROGRESSION);
    public static final Failure FOREIGN_LEVEL = SimpleFailure.of("foreign_level", FailureSources.PROGRESSION);
    public static final Failure SAME_LEVEL = SimpleFailure.of("same_level", FailureSources.PROGRESSION);
    public static final Failure UNKNOWN_LEVEL = SimpleFailure.of("unknown_level", FailureSources.PROGRESSION);

    private ProgressionFailure() {
    }
}
