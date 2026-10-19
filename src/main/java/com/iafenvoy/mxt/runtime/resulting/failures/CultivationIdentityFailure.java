package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

public final class CultivationIdentityFailure {
    public static final Failure ALREADY_HELD = SimpleFailure.of("already_held");
    public static final Failure EXCLUSIVE_CONFLICT = SimpleFailure.of("exclusive_conflict");
    public static final Failure ELEMENT_CONFLICT = SimpleFailure.of("element_conflict");

    private CultivationIdentityFailure() {
    }
}
