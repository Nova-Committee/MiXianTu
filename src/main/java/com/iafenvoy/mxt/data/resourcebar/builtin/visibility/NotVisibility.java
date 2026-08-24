package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.MapCodec;

public record NotVisibility(ResourceBarVisibility value) implements ResourceBarVisibility {
    public static final MapCodec<NotVisibility> CODEC = ResourceBarVisibility.CODEC.fieldOf("value")
            .xmap(NotVisibility::new, NotVisibility::value);

    @Override
    public boolean visible(ResourceBarView view) {
        return !this.value.visible(view);
    }

    @Override
    public MapCodec<NotVisibility> codec() {
        return CODEC;
    }
}
