package com.iafenvoy.mxt.runtime.failure;

public final class FlightFailure {
    public static final Failure NOT_FLYABLE = SimpleFailure.of("not_flyable", FailureSources.ARTIFACT);
    public static final Failure NO_VEHICLE = SimpleFailure.of("no_vehicle", FailureSources.ARTIFACT);
    public static final Failure NOT_OWNED = SimpleFailure.of("not_owned", FailureSources.ARTIFACT);
    public static final Failure COLLISION = SimpleFailure.of("collision", FailureSources.ARTIFACT);
    public static final Failure STOPPED = SimpleFailure.of("stopped", FailureSources.ARTIFACT);
    public static final Failure CANNOT_MOUNT = SimpleFailure.of("cannot_mount", FailureSources.ARTIFACT);
    public static final Failure MOUNT_LOST = SimpleFailure.of("mount_lost", FailureSources.ARTIFACT);
    public static final Failure INVALID_VEHICLE = SimpleFailure.of("invalid_vehicle", FailureSources.ARTIFACT);

    private FlightFailure() {
    }
}
