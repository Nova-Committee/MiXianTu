package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

public final class FormationWorldFailure {
    public static final Failure OCCUPIED = SimpleFailure.of("occupied", FailureSources.FORMATION);
    public static final Failure INVALID_STRUCTURE = SimpleFailure.of("invalid_structure", FailureSources.FORMATION);
    public static final Failure NOT_CLAIMED = SimpleFailure.of("not_claimed", FailureSources.FORMATION);
    public static final Failure FOREIGN_CLAIM = SimpleFailure.of("foreign_claim", FailureSources.FORMATION);
    public static final Failure ACTIVATION_FAILED = SimpleFailure.of("activation_failed", FailureSources.FORMATION);

    private FormationWorldFailure() {
    }
}
