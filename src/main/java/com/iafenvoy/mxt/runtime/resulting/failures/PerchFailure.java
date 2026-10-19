package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

// NOT_WILLING covers both "the creature refused this vehicle" and "the creature does not implement the
// contract at all": either way no seat was offered.
public final class PerchFailure {
    public static final Failure SELF = SimpleFailure.of("self");
    public static final Failure INVALID_OFFSET = SimpleFailure.of("invalid_offset");
    public static final Failure ALREADY_RIDING = SimpleFailure.of("already_riding");
    public static final Failure FULL = SimpleFailure.of("full");
    public static final Failure REFUSED = SimpleFailure.of("refused");
    public static final Failure NOT_WILLING = SimpleFailure.of("not_willing");

    private PerchFailure() {
    }
}
