package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.MapCodec;

public enum AlwaysVisibility implements ResourceBarVisibility {
    INSTANCE;

    public static final MapCodec<AlwaysVisibility> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean visible(ResourceBarView view) {
        return true;
    }

    @Override
    public MapCodec<AlwaysVisibility> codec() {
        return CODEC;
    }
}
