package com.iafenvoy.mxt.runtime.failure;

public final class QualityUpgradeFailure {
    public static final Failure EMPTY = SimpleFailure.of("empty", FailureSources.ITEM_QUALITY);
    public static final Failure NO_QUALITY = SimpleFailure.of("no_quality", FailureSources.ITEM_QUALITY);
    public static final Failure NO_CHAIN = SimpleFailure.of("no_chain", FailureSources.ITEM_QUALITY);
    public static final Failure AT_TOP = SimpleFailure.of("at_top", FailureSources.ITEM_QUALITY);

    private QualityUpgradeFailure() {
    }
}
