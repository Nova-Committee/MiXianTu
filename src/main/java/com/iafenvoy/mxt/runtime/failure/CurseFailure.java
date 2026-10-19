package com.iafenvoy.mxt.runtime.failure;

public final class CurseFailure {
    /**
     * The application condition rejected the entity.
     */
    public static final Failure CONDITION = SimpleFailure.of("condition", FailureSources.CURSE);
    /**
     * The definition is not in the registry any more.
     */
    public static final Failure UNKNOWN = SimpleFailure.of("unknown", FailureSources.CURSE);
    /**
     * The same curse is already mid-transaction for this entity.
     */
    public static final Failure REENTRANT = SimpleFailure.of("reentrant", FailureSources.CURSE);
    /**
     * The resolved duration cannot be honoured, so nothing was written.
     */
    public static final Failure INVALID_DURATION = SimpleFailure.of("invalid_duration", FailureSources.CURSE);

    private CurseFailure() {
    }
}
