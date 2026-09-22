package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The read-only look at a running session that {@link com.iafenvoy.mxt.event.ForgingEvent} hands to listeners:
 * everything a listener may know about the session, and nothing it can do to it. A pre event may cancel or
 * replace the costs but not touch the session's value, history or quality, and the constructor is package
 * private, so a view is only ever one the service made.
 */
public final class ForgingSessionView {
    private final ForgingSession session;

    ForgingSessionView(ForgingSession session) {
        this.session = session;
    }

    public int value() {
        return this.session.value();
    }

    public int steps() {
        return this.session.steps();
    }

    // The number extraSteps is measured against.
    public int optimalSteps() {
        return this.session.optimalSteps();
    }

    public List<Identifier> history() {
        return this.session.history();
    }

    // The same predicate the server uses, asked of the same object.
    public boolean canComplete() {
        return this.session.canComplete();
    }
}
