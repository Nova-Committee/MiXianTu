package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public enum OwnedByItemCondition implements ItemCondition {
    INSTANCE;
    public static final MapCodec<OwnedByItemCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ArtifactService.state(ctx.stack()).ownerUuid().filter(value -> value.equals(ctx.holder().getUUID().toString())).isPresent();
    }

    @Override
    public @NonNull MapCodec<OwnedByItemCondition> codec() {
        return CODEC;
    }
}
