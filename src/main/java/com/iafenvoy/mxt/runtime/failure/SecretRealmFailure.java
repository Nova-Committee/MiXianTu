package com.iafenvoy.mxt.runtime.failure;

public final class SecretRealmFailure {
    public static final Failure FULL = SimpleFailure.of("full", FailureSources.SECRET_REALM);
    public static final Failure MISSING_DIMENSION = SimpleFailure.of("missing_dimension", FailureSources.SECRET_REALM);
    public static final Failure ALREADY_TRAVELLING = SimpleFailure.of("already_travelling", FailureSources.SECRET_REALM);
    public static final Failure NOT_TRAVELLING = SimpleFailure.of("not_travelling", FailureSources.SECRET_REALM);
    public static final Failure MISSING_ORIGIN = SimpleFailure.of("missing_origin", FailureSources.SECRET_REALM);
    public static final Failure GENERATION_FAILED = SimpleFailure.of("generation_failed", FailureSources.SECRET_REALM);
    public static final Failure MISSING_STRUCTURE = SimpleFailure.of("missing_structure", FailureSources.SECRET_REALM);
    public static final Failure NO_FREE_INSTANCE = SimpleFailure.of("no_free_instance", FailureSources.SECRET_REALM);
    public static final Failure CONDITION_NOT_MET = SimpleFailure.of("condition_not_met", FailureSources.SECRET_REALM);
    public static final Failure EXIT_DENIED = SimpleFailure.of("exit_denied", FailureSources.SECRET_REALM);
    public static final Failure TELEPORT_FAILED = SimpleFailure.of("teleport_failed", FailureSources.SECRET_REALM);

    private SecretRealmFailure() {
    }
}
