package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** An on/off state. {@code default} is what an unwritten value reads as and {@code state} is the flag. */
public final class ToggleDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by, and the reading of a toggle nothing has written yet.
    public static final ToggleDataStorage INSTANCE = new ToggleDataStorage(false, Optional.empty());
    public static final MapCodec<ToggleDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("default", false).forGetter(ToggleDataStorage::defaultValue),
            Codec.BOOL.optionalFieldOf("state").forGetter(ToggleDataStorage::state)
    ).apply(i, ToggleDataStorage::new));
    private final boolean defaultValue;
    private Optional<Boolean> state;

    private ToggleDataStorage(boolean defaultValue, Optional<Boolean> state) {
        this.defaultValue = defaultValue;
        this.state = state;
    }

    @Override
    public MapCodec<ToggleDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new ToggleDataStorage(this.defaultValue, this.state);
    }

    public boolean defaultValue() {
        return this.defaultValue;
    }

    public Optional<Boolean> state() {
        return this.state;
    }

    public boolean value() {
        return this.state.orElse(this.defaultValue);
    }

    public void set(boolean value) {
        this.state = Optional.of(value);
        this.markDirty();
    }
}
