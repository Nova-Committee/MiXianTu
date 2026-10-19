package com.iafenvoy.mxt.runtime.resulting;

public final class Failures {
    public static final Failure UNKNOWN = SimpleFailure.of("unknown");
    public static final Failure SERVER_ONLY = SimpleFailure.of("server_only");
    public static final Failure DISABLED = SimpleFailure.of("disabled");
    public static final Failure CANCELLED = SimpleFailure.of("cancelled");
    public static final Failure COOLDOWN = SimpleFailure.of("cooldown");
    public static final Failure CONDITIONS = SimpleFailure.of("conditions");
    public static final Failure CONDITION_FAILED = SimpleFailure.of("condition_failed");
    public static final Failure INVALID_FORMULA = SimpleFailure.of("invalid_formula");
    public static final Failure INVALID_VALUE = SimpleFailure.of("invalid_value");
    public static final Failure INSUFFICIENT_RESOURCE = SimpleFailure.of("insufficient_resource");
    public static final Failure INSUFFICIENT_COST = SimpleFailure.of("insufficient_cost");
    public static final Failure NOT_APPLICABLE = SimpleFailure.of("not_applicable");
    public static final Failure NO_TARGET = SimpleFailure.of("no_target");
    public static final Failure ALREADY_ACTIVE = SimpleFailure.of("already_active");
    public static final Failure MAX_USES = SimpleFailure.of("max_uses");
}
