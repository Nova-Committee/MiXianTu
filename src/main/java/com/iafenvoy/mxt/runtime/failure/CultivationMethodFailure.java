package com.iafenvoy.mxt.runtime.failure;

public final class CultivationMethodFailure {
    public static final Failure NOT_ACTIVE = SimpleFailure.of("not_active", FailureSources.CULTIVATION);
    public static final Failure ENVIRONMENT = SimpleFailure.of("environment", FailureSources.CULTIVATION);
    public static final Failure INSUFFICIENT_AURA = SimpleFailure.of("insufficient_aura", FailureSources.CULTIVATION);

    private CultivationMethodFailure() {
    }
}
