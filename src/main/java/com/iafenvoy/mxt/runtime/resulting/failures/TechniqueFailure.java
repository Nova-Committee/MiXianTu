package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

public final class TechniqueFailure {
    public static final Failure ALREADY_LEARNED = SimpleFailure.of("already_learned", FailureSources.TECHNIQUE);
    public static final Failure CONFLICT = SimpleFailure.of("conflict", FailureSources.TECHNIQUE);
    public static final Failure ABSENT = SimpleFailure.of("absent", FailureSources.TECHNIQUE);

    private TechniqueFailure() {
    }
}
