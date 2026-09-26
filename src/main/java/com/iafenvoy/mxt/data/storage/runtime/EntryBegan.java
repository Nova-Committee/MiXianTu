package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.MapCodec;

/**
 * The marker a run writes when the current entry begins, and the state an entry that keeps no numbers of its own
 * leaves behind. It has to be stored rather than derived: it is what tells a run coming back from a save that the
 * first tick of the entry already happened, so its start is not consumed twice.
 */
public final class EntryBegan extends DataStorage {
    public static final EntryBegan INSTANCE = new EntryBegan();
    public static final MapCodec<EntryBegan> CODEC = MapCodec.unit(INSTANCE);

    private EntryBegan() {
    }

    @Override
    public MapCodec<EntryBegan> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return this;
    }
}
