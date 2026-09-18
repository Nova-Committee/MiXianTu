package com.iafenvoy.mxt.data.storage;

/**
 * A kind a runtime keeps for itself. It is registered so that its values can be saved and synced like any
 * other, but no content declares it and no generic writer may touch it: these hold live cursors whose meaning
 * belongs to the runtime that reads them.
 */
public interface RuntimeStorage extends DataStorage {
}