package com.iafenvoy.mxt.runtime.failure;

public final class LifeSpanFailure {
    public static final Failure INVALID_VALUE = SimpleFailure.of("invalid_value");

    private LifeSpanFailure() {
    }
}
