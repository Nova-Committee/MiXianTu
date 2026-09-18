package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * A kind of state a host keeps, and the value kept for it. The instance a host stores IS this object: the
 * fields that describe the declaration and the fields that carry the current state live in the same record, and
 * one {@code type} field tells the two apart on the way back out of saved data.
 *
 * <p>Its own class is what addresses that value, so nothing else has to name a slot, and a reader who asked
 * for this implementation gets this implementation back. A kind that keeps state declares it as a field; a kind
 * that has none yet simply has none.</p>
 */
public interface DataStorage {
    Codec<DataStorage> CODEC = MxtRegistries.DATA_STORAGE_TYPE.byNameCodec().dispatch("type", DataStorage::codec, Function.identity());

    MapCodec<? extends DataStorage> codec();
}