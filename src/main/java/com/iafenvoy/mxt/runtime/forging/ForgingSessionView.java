package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The read-only look at a running session that {@link com.iafenvoy.mxt.event.ForgingEvent} hands to
 * listeners.
 *
 * <p>Events used to carry the {@link ForgingSession} itself, and it is mutable: {@code strike} moves the
 * value, appends to the history and counts a step, and the caller writes whatever it holds back into the
 * table afterwards. A listener could therefore edit a session that was still in flight - or keep the
 * reference and edit it later - and the result would be indistinguishable from the player having struck.
 * The events carry this instead: everything a listener may know about the session, and nothing it can do
 * to it. {@code research/06} states the rule the events now enforce: a pre event may cancel or replace the
 * costs, and may not touch the session's value, history or quality.</p>
 *
 * <p>Deliberately a window rather than a snapshot. The reads below are live for exactly as long as the
 * event is being dispatched, which is the same instant either way, and reading through the session is what
 * keeps this class from restating a rule: {@link #canComplete} is the session's own answer, not a second
 * implementation of the completion rule that could drift from it.</p>
 *
 * <p>The constructor is package private, so a view is only ever one the service made. The table itself is
 * not reachable from here either; a listener that has to name the table gets its position from the event.</p>
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
