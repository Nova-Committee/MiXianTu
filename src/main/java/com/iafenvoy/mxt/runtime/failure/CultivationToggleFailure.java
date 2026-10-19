package com.iafenvoy.mxt.runtime.failure;

public final class CultivationToggleFailure {
    public static final Failure NOT_HELD = SimpleFailure.of("not_held", FailureSources.CULTIVATION);

    private CultivationToggleFailure() {
    }
}
