package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

// Why an entity may not use an item. Furnace quality is one of the checks above; pill caps are extra and
// never replace them.
public final class QualityFailure {
    public static final Failure BINDING_CONDITIONS = SimpleFailure.of("binding_conditions", FailureSources.ITEM_QUALITY);
    public static final Failure QUALITY_CONDITIONS = SimpleFailure.of("quality_conditions", FailureSources.ITEM_QUALITY);
    public static final Failure EMPTY = SimpleFailure.of("empty", FailureSources.ITEM_QUALITY);
    public static final Failure NO_QUALITY = SimpleFailure.of("no_quality", FailureSources.ITEM_QUALITY);
    public static final Failure NO_CHAIN = SimpleFailure.of("no_chain", FailureSources.ITEM_QUALITY);
    public static final Failure AT_TOP = SimpleFailure.of("at_top", FailureSources.ITEM_QUALITY);

    private QualityFailure() {
    }
}
