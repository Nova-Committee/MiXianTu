package com.iafenvoy.mxt.runtime.failure;

// Named after AbilityFailure wherever the two mean the same thing: a press and a cast are reported
// through one table of messages, so a reason the cast pipeline already tells apart must not be flattened here.
public final class TogglableFailure {
    public static final Failure NOT_OWNED = SimpleFailure.of("not_owned", FailureSources.ABILITY);
    public static final Failure ALREADY_SET = SimpleFailure.of("already_set", FailureSources.ABILITY);
    // The last resort, for a press no other reason describes.
    public static final Failure UNAVAILABLE = SimpleFailure.of("unavailable", FailureSources.ABILITY);
    // The ability acts on an item and nothing the press offers is carrying one.
    public static final Failure NO_CARRIER = SimpleFailure.of("no_carrier", FailureSources.ABILITY);
    // The ability acts on a vehicle and neither hand is holding one.
    public static final Failure NO_VEHICLE = SimpleFailure.of("no_vehicle", FailureSources.ABILITY);
    public static final Failure CANNOT_MOUNT = SimpleFailure.of("cannot_mount", FailureSources.ABILITY);
    public static final Failure NOT_GRANTED = SimpleFailure.of("not_granted", FailureSources.ABILITY);
    public static final Failure NO_CHARGES = SimpleFailure.of("no_charges", FailureSources.ABILITY);
    public static final Failure PERMISSION_DENIED = SimpleFailure.of("permission_denied", FailureSources.ABILITY);
    public static final Failure ELEMENT_AFFINITY = SimpleFailure.of("element_affinity", FailureSources.ABILITY);

    private TogglableFailure() {
    }
}
