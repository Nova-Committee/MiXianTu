package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.MapCodec;

import java.util.List;

public record OrVisibility(List<ResourceBarVisibility> values) implements ResourceBarVisibility {
    public static final MapCodec<OrVisibility> CODEC = ResourceBarVisibility.CODEC.listOf().fieldOf("values")
            .xmap(OrVisibility::new, OrVisibility::values);

    @Override
    public boolean visible(ResourceBarView view) {
        return this.values.stream().anyMatch(value -> value.visible(view));
    }

    @Override
    public MapCodec<OrVisibility> codec() {
        return CODEC;
    }
}
