package com.iafenvoy.mxt.data.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers the kinds one definition declares: a type adds its own on top of the default it was handed, and every add
 * is kept, so the one-class-each rule stays a check the definition reports rather than a value silently replaced.
 */
public final class DataStorageCollector {
    private final List<DataStorage> values = new ArrayList<>();

    private DataStorageCollector() {
    }

    public static DataStorageCollector create() {
        return new DataStorageCollector();
    }

    public void add(DataStorage value) {
        this.values.add(value);
    }

    public List<DataStorage> build() {
        return List.copyOf(this.values);
    }
}
