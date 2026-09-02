package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.MapCodec;

/**
 * Shows a bar only when its configured range has a positive size.
 */
public enum NonZeroVisibility implements ResourceBarVisibility {
    INSTANCE;

    public static final MapCodec<NonZeroVisibility> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean visible(ResourceBarView view) {
        return view.maximum() > view.minimum();
    }

    @Override
    public MapCodec<NonZeroVisibility> codec() {
        return CODEC;
    }
}
