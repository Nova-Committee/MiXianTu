package com.iafenvoy.mxt.data.timeline;

/**
 * Where a run continues once the beat being consumed has finished, as a request a beat leaves behind rather than a
 * return value: an entry answers with {@link TimelineEntry.Outcome}, and only the consumer may move the cursor. An
 * untouched request means the next beat.
 */
public final class TimelineJump {
    // No beat asked for anything, which is every beat that is not a branch.
    public static final int NEXT = -1;
    private int target = NEXT;

    public void to(int index) {
        this.target = index;
    }

    public int target() {
        return this.target;
    }

    public boolean requested() {
        return this.target != NEXT;
    }

    public void clear() {
        this.target = NEXT;
    }
}
