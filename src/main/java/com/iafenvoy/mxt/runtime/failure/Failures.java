package com.iafenvoy.mxt.runtime.failure;

public final class Failures {
    // Common
    public static final Failure SERVER_ONLY = SimpleFailure.of("server_only");
    public static final Failure DISABLED = SimpleFailure.of("disabled");
    public static final Failure CANCELLED = SimpleFailure.of("cancelled");
    public static final Failure COOLDOWN = SimpleFailure.of("cooldown");
    public static final Failure CONDITIONS = SimpleFailure.of("conditions");
    public static final Failure CONDITION_FAILED = SimpleFailure.of("condition_failed");
    public static final Failure INVALID_FORMULA = SimpleFailure.of("invalid_formula");
    public static final Failure INSUFFICIENT_RESOURCE = SimpleFailure.of("insufficient_resource");
    public static final Failure INSUFFICIENT_COST = SimpleFailure.of("insufficient_cost");
    public static final Failure NOT_APPLICABLE = SimpleFailure.of("not_applicable");
    public static final Failure NO_TARGET = SimpleFailure.of("no_target");
    public static final Failure ALREADY_ACTIVE = SimpleFailure.of("already_active");
    public static final Failure MAX_USES = SimpleFailure.of("max_uses");

    /** Every shared refusal, for a module that has to tell one from its own. */
    public static final java.util.Set<Failure> ALL = java.util.Set.of(SERVER_ONLY, DISABLED, CANCELLED, COOLDOWN, CONDITIONS,
            CONDITION_FAILED, INVALID_FORMULA, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST, NOT_APPLICABLE, NO_TARGET,
            ALREADY_ACTIVE, MAX_USES);
    private static final java.util.Set<String> SHARED_IDS = ALL.stream().map(Failure::getSerializedName)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** Whether this refusal is one of the shared names, whichever module's instance carries it. */
    public static boolean isShared(Failure failure) {
        return failure != null && SHARED_IDS.contains(failure.getSerializedName());
    }
}
