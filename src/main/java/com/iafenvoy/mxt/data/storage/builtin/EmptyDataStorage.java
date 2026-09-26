package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.MapCodec;

/**
 * The registry's default kind: it declares nothing and keeps nothing, and exists only so a definition can say it
 * keeps no state of its own.
 */
public final class EmptyDataStorage extends DataStorage {
    public static final EmptyDataStorage INSTANCE = new EmptyDataStorage();
    public static final MapCodec<EmptyDataStorage> CODEC = MapCodec.unit(INSTANCE);

    private EmptyDataStorage() {
    }

    @Override
    public MapCodec<EmptyDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return this;
    }
}
