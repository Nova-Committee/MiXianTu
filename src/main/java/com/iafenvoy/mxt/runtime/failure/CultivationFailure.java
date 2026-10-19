package com.iafenvoy.mxt.runtime.failure;

// Everything here is reachable: a stage may not require less progress than its own cap (see threshold()), so
// "the cap is holding the progress back" can never be the reason a breakthrough was refused.
public final class CultivationFailure {
    public static final Failure WRONG_AURA = SimpleFailure.of("wrong_aura", FailureSources.REALM_STAGE);
    public static final Failure NO_NEXT_REALM = SimpleFailure.of("no_next_realm", FailureSources.REALM_STAGE);
    public static final Failure INSUFFICIENT_PROGRESS = SimpleFailure.of("insufficient_progress", FailureSources.REALM_STAGE);

    private CultivationFailure() {
    }
}
