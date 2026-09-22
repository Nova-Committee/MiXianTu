package com.iafenvoy.mxt.data.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One stored value and the tick it was written. The value travels as the kind instance itself, so the store can
 * read and write it without knowing anything about it, and the write tick is the one thing every kind would
 * otherwise keep for itself.
 */
public record StoredData(DataStorage value, long changedAt) {
    public static final Codec<StoredData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DataStorage.CODEC.fieldOf("value").forGetter(StoredData::value),
            Codec.LONG.optionalFieldOf("changed_at", 0L).forGetter(StoredData::changedAt)
    ).apply(i, StoredData::new));
}