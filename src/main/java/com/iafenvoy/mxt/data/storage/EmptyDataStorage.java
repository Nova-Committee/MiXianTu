package com.iafenvoy.mxt.data.storage;

import com.mojang.serialization.MapCodec;

/**
 * The registry's default kind: it declares nothing and keeps nothing. It exists only so that a definition can
 * say it keeps no state of its own.
 */
public enum EmptyDataStorage implements DataStorage {
    INSTANCE;
    public static final MapCodec<EmptyDataStorage> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EmptyDataStorage> codec() {
        return CODEC;
    }
}