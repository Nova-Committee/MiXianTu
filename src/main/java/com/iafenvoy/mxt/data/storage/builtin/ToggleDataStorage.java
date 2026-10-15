package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.OptionalBoolean;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An on/off state. {@code default} is what an unwritten value reads as and {@code state} is the flag.
 */
public final class ToggleDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by, and the reading of a toggle nothing has written yet.
    public static final ToggleDataStorage INSTANCE = new ToggleDataStorage(false, OptionalBoolean.empty());
    public static final MapCodec<ToggleDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("default", false).forGetter(ToggleDataStorage::defaultValue),
            OptionalCodecs.boolCodec("state").forGetter(ToggleDataStorage::state)
    ).apply(i, ToggleDataStorage::new));
    private final boolean defaultValue;
    private OptionalBoolean state;

    private ToggleDataStorage(boolean defaultValue, OptionalBoolean state) {
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

    public OptionalBoolean state() {
        return this.state;
    }

    public boolean value() {
        return this.state.orElse(this.defaultValue);
    }

    public void set(boolean value) {
        this.state = OptionalBoolean.of(value);
        this.markDirty();
    }
}
