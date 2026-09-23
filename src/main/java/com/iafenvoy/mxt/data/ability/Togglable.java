package com.iafenvoy.mxt.data.ability;

import net.minecraft.resources.Identifier;
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

    record Result(boolean changed, @Nullable Failure failure, @Nullable Identifier failedResource) {
        public static Result activated() {
            return new Result(true, null, null);
        }

        public static Result refused(Failure failure) {
            return new Result(false, failure, null);
        }

        // The payer ran out of one named resource, which is what lets the report say which one.
        public static Result refused(Failure failure, @Nullable Identifier failedResource) {
            return new Result(false, failure, failedResource);
        }
    }

    // Named after AbilityService.Failure wherever the two mean the same thing: a press and a cast are reported
    // through one table of messages, so a reason the cast pipeline already tells apart must not be flattened here.
    enum Failure {
        NOT_OWNED,
        ALREADY_SET,
        // The last resort, for a press no other reason describes.
        UNAVAILABLE,
        // The ability acts on an item and nothing the press offers is carrying one.
        NO_CARRIER,
        CANNOT_MOUNT,
        NOT_GRANTED,
        COOLDOWN,
        INSUFFICIENT_RESOURCE,
        INSUFFICIENT_COST,
        INVALID_FORMULA,
        CONDITION_FAILED,
        NO_CHARGES,
        CANCELLED,
        PERMISSION_DENIED,
        ELEMENT_AFFINITY
    }
}
