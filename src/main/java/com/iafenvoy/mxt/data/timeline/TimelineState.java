package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.data.storage.DataStorage;

import java.util.Optional;

/**
 * The live state of the one entry a run is currently on. A run keeps a single value rather than a store, because a
 * timeline consumes one entry at a time. The consumer hands an entry a draft and commits it once the entry has
 * answered, so an entry can never change the stored value except through the tick that produced it.
 */
public final class TimelineState {
    private DataStorage value;

    public TimelineState() {
    }

    public TimelineState(DataStorage value) {
        this.value = value;
    }

    public Optional<DataStorage> get() {
        return Optional.ofNullable(this.value);
    }

    // An entry reads empty rather than another entry's numbers.
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
