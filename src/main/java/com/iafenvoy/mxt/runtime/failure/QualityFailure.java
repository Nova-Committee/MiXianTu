package com.iafenvoy.mxt.runtime.failure;

// Why an entity may not use an item. Furnace quality is one of the checks above; pill caps are extra and
// never replace them.
public final class QualityFailure {
    /**
     * A matching binding's own conditions did not all pass.
     */
    public static final Failure BINDING_CONDITIONS = SimpleFailure.of("binding_conditions", FailureSources.ITEM_QUALITY);
    /**
     * The condition of the item's resolved quality did not pass.
     */
    public static final Failure QUALITY_CONDITIONS = SimpleFailure.of("quality_conditions", FailureSources.ITEM_QUALITY);

    private QualityFailure() {
    }
}
