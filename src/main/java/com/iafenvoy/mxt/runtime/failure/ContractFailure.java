package com.iafenvoy.mxt.runtime.failure;

public final class ContractFailure {
    public static final Failure ALREADY_BOUND = SimpleFailure.of("already_bound", FailureSources.CONTRACT_TYPE);
    public static final Failure NOT_CONTRACTABLE = SimpleFailure.of("not_contractable", FailureSources.CONTRACT_TYPE);
    public static final Failure OWNER_CONDITIONS = SimpleFailure.of("owner_conditions", FailureSources.CONTRACT_TYPE);
    public static final Failure CREATURE_CONDITIONS = SimpleFailure.of("creature_conditions", FailureSources.CONTRACT_TYPE);
    public static final Failure LIMIT_REACHED = SimpleFailure.of("limit_reached", FailureSources.CONTRACT_TYPE);
    public static final Failure NOT_BOUND = SimpleFailure.of("not_bound", FailureSources.CONTRACT_TYPE);
    public static final Failure NOT_OWNER = SimpleFailure.of("not_owner", FailureSources.CONTRACT_TYPE);
    public static final Failure RECALL_COOLDOWN = SimpleFailure.of("recall_cooldown", FailureSources.CONTRACT_TYPE);
    public static final Failure RECALL_PENDING = SimpleFailure.of("recall_pending", FailureSources.CONTRACT_TYPE);
    public static final Failure UNSUPPORTED_BEHAVIOR = SimpleFailure.of("unsupported_behavior", FailureSources.CONTRACT_TYPE);
    public static final Failure BEHAVIOR_REFUSED = SimpleFailure.of("behavior_refused", FailureSources.CONTRACT_TYPE);

    private ContractFailure() {
    }
}
