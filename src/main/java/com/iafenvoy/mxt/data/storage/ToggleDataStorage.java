package com.iafenvoy.mxt.data.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * An on/off state. {@code defaultValue} is what an unwritten value reads as and {@code state} is the flag.
 */
public record ToggleDataStorage(boolean defaultValue, Optional<Boolean> state) implements DataStorage {
    public static final MapCodec<ToggleDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("default", false).forGetter(ToggleDataStorage::defaultValue),
            Codec.BOOL.optionalFieldOf("state").forGetter(ToggleDataStorage::state)
    ).apply(i, ToggleDataStorage::new));

    @Override
    public MapCodec<ToggleDataStorage> codec() {
        return CODEC;
    }

    public ToggleDataStorage withState(boolean value) {
        return new ToggleDataStorage(this.defaultValue, Optional.of(value));
    }
}