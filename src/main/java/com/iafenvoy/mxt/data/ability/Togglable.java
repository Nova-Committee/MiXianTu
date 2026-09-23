package com.iafenvoy.mxt.data.ability;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Something the wheel can trigger. Implementing it is the whole of saying "put me on the wheel": the entry's kind
 * no longer decides that, so every registered ability that grows the interface is addressed the same way.
 *
 * <p>Where the ability lives is the caller's business, not this contract's: {@link ToggleContext#carrier()} is
 * present when a carried item asked for the press and empty when a grant did.
 */
public interface Togglable {
    // Empty is the ordinary case for a one-shot: nothing stays on, so there is no state to report (storage).
    default Optional<Boolean> state(ToggleContext context) {
        return Optional.empty();
    }

    // Whether the press pays through the shared gate first. A cast type pays inside its own transaction, and a
    // switch being turned off pays nothing, so both answer false here.
    default boolean gated(ToggleContext context) {
        return true;
    }

    // One call per press, server side only, which is why a refusal is a value rather than an exception.
    Result activate(ToggleContext context);

    record Result(boolean changed, @Nullable Failure failure) {
        public static Result activated() {
            return new Result(true, null);
        }

        public static Result refused(Failure failure) {
            return new Result(false, failure);
        }
    }

    enum Failure {
        // For the wheel this is normally a race between the page it read and the trigger, which the server drops
        // as a stale entry before an implementation is ever asked.
        NOT_CARRIED,
        NOT_OWNED,
        ALREADY_SET,
        UNAVAILABLE,
        // The ability needs an item to act on and none of its holders is carrying one.
        NO_CARRIER,
        INSUFFICIENT_COST,
        ON_COOLDOWN
    }
}
