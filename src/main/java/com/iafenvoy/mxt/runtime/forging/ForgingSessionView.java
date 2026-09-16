package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The read-only look at a running session that {@link com.iafenvoy.mxt.event.ForgingEvent} hands to
 * listeners: everything a listener may know about the session, and nothing it can do to it. A pre event
 * may cancel or replace the costs but not touch the session's value, history or quality; the constructor
 * is package private, so a view is only ever one the service made.
 */
public final class ForgingSessionView {
    private final ForgingSession session;

    ForgingSessionView(ForgingSession session) {
        this.session = session;
    }

    /**
     * The current meter value.
     */
    public int value() {
        return this.session.value();
    }

    /**
     * How many steps have been struck.
     */
    public int steps() {
        return this.session.steps();
    }

    /**
     * The shortest run the session's plan allows - the number {@code extraSteps} is measured against.
     */
    public int optimalSteps() {
        return this.session.optimalSteps();
    }

    /**
     * The methods struck so far, oldest first, as an immutable copy.
     */
    public List<Identifier> history() {
        return this.session.history();
    }

    /**
     * Whether the session would settle right now: the value inside the target band and the finish pattern
     * satisfied. The same predicate the server uses, asked of the same object.
     */
    public boolean canComplete() {
        return this.session.canComplete();
    }
}
