package com.iafenvoy.mxt.runtime.resulting;

/**
 * A refusal that is not a constant of any enum: one a command or a script synthesises, or one whose text is plain.
 */
public record SimpleFailure(String name, FailureSource source) implements Failure {
    public static SimpleFailure of(String id) {
        return new SimpleFailure(id, FailureSources.COMMON);
    }

    public static SimpleFailure of(String id, FailureSource source) {
        return new SimpleFailure(id, source);
    }
}
