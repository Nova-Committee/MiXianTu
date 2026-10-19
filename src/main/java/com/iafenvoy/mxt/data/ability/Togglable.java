package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.util.OptionalBoolean;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import com.iafenvoy.mxt.runtime.failure.Failure;

/**
 * Something the wheel can trigger. Implementing it is the whole of saying "put me on the wheel": the entry's kind
 * no longer decides that, so every registered ability that grows the interface is addressed the same way.
 *
 * <p>Where the ability lives is the caller's business, not this contract's: {@link ToggleContext#carrier()} is
 * present when a carried item asked for the press and empty when a grant did.
 */
public interface Togglable {
    // Empty is the ordinary case for a one-shot: nothing stays on, so there is no state to report (storage).
    default OptionalBoolean state(ToggleContext context) {
        return OptionalBoolean.empty();
    }

    // Whether the press pays through the shared gate first. A cast type pays inside its own transaction, and a
    // switch being turned off pays nothing, so both answer false here.
    default boolean gated(ToggleContext context) {
        return true;
    }

    // Asked before the gate pays: a press that cannot happen must not cost anything. Empty is the ordinary case.
    // Only what can be answered by reading belongs here, and a type that answers still re-checks inside activate.
    default Optional<Failure> canActivate(ToggleContext context) {
        return Optional.empty();
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
}
