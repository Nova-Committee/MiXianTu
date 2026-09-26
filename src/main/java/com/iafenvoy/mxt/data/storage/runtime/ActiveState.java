package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * Whether an ability's condition held the last time the runtime looked, which is what turns "the condition passes"
 * into the one moment a type is told it became active or inactive. Every type declares it; the tick loop is what
 * writes it.
 */
public final class ActiveState extends DataStorage {
    public static final ActiveState NONE = new ActiveState(false);
    public static final MapCodec<ActiveState> CODEC = Codec.BOOL.fieldOf("active").xmap(ActiveState::new, ActiveState::active);
    private boolean active;

    public ActiveState(boolean active) {
        this.active = active;
    }

    @Override
    public MapCodec<ActiveState> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new ActiveState(this.active);
    }

    public boolean active() {
        return this.active;
    }

    public void set(boolean active) {
        this.active = active;
        this.markDirty();
    }
}
