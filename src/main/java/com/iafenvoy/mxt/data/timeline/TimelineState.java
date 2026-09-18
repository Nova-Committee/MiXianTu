package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.data.storage.DataStorage;

import java.util.Optional;

/**
 * The live state of the one entry a run is currently on. A timeline consumes a single entry at a time, so a run
 * keeps a single value instead of a store: the entry writes its own temporary numbers here and reads them back on
 * the next tick, and what is left behind at the end of a tick is what gets saved with the run.
 *
 * <p>The consumer hands an entry a draft of this and commits it once the entry has answered, so an entry can
 * never change the stored value except through the tick that produced it.</p>
 */
public final class TimelineState {
    private DataStorage value;

    public TimelineState() {
    }

    public TimelineState(DataStorage value) {
        this.value = value;
    }

    /**
     * The state as it stands, or empty while the current entry has not written anything.
     */
    public Optional<DataStorage> get() {
        return Optional.ofNullable(this.value);
    }

    /**
     * The state when it is of the asked-for kind; an entry reads empty rather than another entry's numbers.
     */
    public <T extends DataStorage> Optional<T> get(Class<T> kind) {
        return this.get().filter(kind::isInstance).map(kind::cast);
    }

    public boolean isPresent() {
        return this.value != null;
    }

    public void set(DataStorage value) {
        this.value = value;
    }
}
