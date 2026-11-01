package com.iafenvoy.mxt.runtime.resulting;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.FailureSource;
import com.iafenvoy.saturn.runtime.resulting.LocatedFailure;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A failure that keeps the failure's own name and source but shows a pack-provided message, for a definition that
 * names its own abort or denial text. The message travels inside the failure, so callers read it the same way they
 * read any other failure.
 */
public record MessageFailure(Failure reason, Component message) implements Failure {
    /**
     * The plain reason behind a decorated failure: a custom message and a named resource are wrappers around one
     * reason, and code that compares reasons (a test asserting the exact constant) has to peel them first.
     * TODO: move to Saturn once its failure API carries the same helper.
     */
    public static Failure reason(Failure failure) {
        Failure current = failure;
        while (true) {
            if (current instanceof MessageFailure message) current = message.reason();
            else if (current instanceof LocatedFailure located) current = located.reason();
            else return current;
        }
    }

    @Override
    public String name() {
        return this.reason.name();
    }

    @Override
    public FailureSource source() {
        return this.reason.source();
    }

    @Override
    public Optional<Identifier> resource() {
        return this.reason.resource();
    }

    @Override
    public Component message() {
        return this.message;
    }

    @Override
    public Component message(Identifier resource) {
        return this.message;
    }
}
