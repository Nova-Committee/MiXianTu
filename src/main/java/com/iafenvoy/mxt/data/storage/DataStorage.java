package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * A kind of state a host keeps, and the value kept for it. The instance a host stores IS this object: declaration
 * fields and current-state fields live in the same record, and one {@code type} field tells the two apart on the
 * way back out of saved data. Its own class is what addresses that value, so nothing else has to name a slot.
 */
public interface DataStorage {
    Codec<DataStorage> CODEC = MxtRegistries.DATA_STORAGE_TYPE.byNameCodec().dispatch("type", DataStorage::codec, Function.identity());

    MapCodec<? extends DataStorage> codec();

    // The kind's registered id, for the messages that turn a duplicate down; a kind nothing registered falls back
    // to its class name.
    static String name(DataStorage value) {
        return MxtRegistries.DATA_STORAGE_TYPE.getKey(value.codec()).toString();
    }
}