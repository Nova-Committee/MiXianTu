package com.iafenvoy.mxt.runtime.failure;

public final class AbilityFailure {
    public static final Failure NOT_GRANTED = SimpleFailure.of("not_granted", FailureSources.ABILITY);
    public static final Failure NO_CHARGES = SimpleFailure.of("no_charges", FailureSources.ABILITY);
    public static final Failure PERMISSION_DENIED = SimpleFailure.of("permission_denied", FailureSources.ABILITY);
    public static final Failure ELEMENT_AFFINITY = SimpleFailure.of("element_affinity", FailureSources.ABILITY);
    public static final Failure CARRIED_NOT_INSTANT = SimpleFailure.of("carried_not_instant", FailureSources.ABILITY);

    private AbilityFailure() {
    }
}
