package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.SimpleFailure;

public final class RiftFailure {
    public static final Failure BUDGET = SimpleFailure.of("budget", FailureSources.RIFT);
    public static final Failure DURATION_OVERFLOW = SimpleFailure.of("duration_overflow", FailureSources.RIFT);
    public static final Failure EXPIRED = SimpleFailure.of("expired", FailureSources.RIFT);
    public static final Failure HOLD_ANCHOR = SimpleFailure.of("hold_anchor", FailureSources.RIFT);
    public static final Failure INVALID_DESTINATION = SimpleFailure.of("invalid_destination", FailureSources.RIFT);
    public static final Failure INVALID_TEMPLATE = SimpleFailure.of("invalid_template", FailureSources.RIFT);
    public static final Failure MISSING_DIMENSION = SimpleFailure.of("missing_dimension", FailureSources.RIFT);
    public static final Failure MISSING_REALM = SimpleFailure.of("missing_realm", FailureSources.RIFT);
    public static final Failure OUTSIDE_WORLD = SimpleFailure.of("outside_world", FailureSources.RIFT);
    public static final Failure PERMISSION = SimpleFailure.of("permission", FailureSources.RIFT);
    public static final Failure REVISION_CONFLICT = SimpleFailure.of("revision_conflict", FailureSources.RIFT);

    private RiftFailure() {
    }
}
