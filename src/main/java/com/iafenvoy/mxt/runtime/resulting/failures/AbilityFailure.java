package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

public final class AbilityFailure {
    public static final Failure NOT_GRANTED = SimpleFailure.of("not_granted", FailureSources.ABILITY);
    public static final Failure NO_CHARGES = SimpleFailure.of("no_charges", FailureSources.ABILITY);
    public static final Failure PERMISSION_DENIED = SimpleFailure.of("permission_denied", FailureSources.ABILITY);
    public static final Failure ELEMENT_AFFINITY = SimpleFailure.of("element_affinity", FailureSources.ABILITY);
    public static final Failure CARRIED_NOT_INSTANT = SimpleFailure.of("carried_not_instant", FailureSources.ABILITY);
    // The ability acts on an item and the press offers nothing carrying one, or offers one somebody else owns.
    public static final Failure NO_CARRIER = SimpleFailure.of("no_carrier", FailureSources.ABILITY);
    public static final Failure NOT_OWNED = SimpleFailure.of("not_owned", FailureSources.ABILITY);
    // The last resort, for a press no other reason describes.
    public static final Failure UNAVAILABLE = SimpleFailure.of("unavailable", FailureSources.ABILITY);

    private AbilityFailure() {
    }
}
