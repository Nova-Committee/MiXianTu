package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.MapCodec;

import java.util.List;

public record AndVisibility(List<ResourceBarVisibility> values) implements ResourceBarVisibility {
    public static final MapCodec<AndVisibility> CODEC = ResourceBarVisibility.CODEC.listOf().fieldOf("values")
            .xmap(AndVisibility::new, AndVisibility::values);

    @Override
    public boolean visible(ResourceBarView view) {
        return this.values.stream().allMatch(value -> value.visible(view));
    }

    @Override
    public MapCodec<AndVisibility> codec() {
        return CODEC;
    }
}
