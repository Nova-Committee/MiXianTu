package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.RuntimeStorage;
import com.mojang.serialization.MapCodec;

/**
 * The marker a run writes when the current entry begins, and the state an entry that keeps no numbers of its own
 * leaves behind. It has to be stored rather than derived: it is what tells a run coming back from a save that the
 * first tick of the entry already happened, so its start is not consumed twice.
 */
public enum EntryBegan implements RuntimeStorage {
    INSTANCE;
    public static final MapCodec<EntryBegan> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EntryBegan> codec() {
        return CODEC;
    }
}
