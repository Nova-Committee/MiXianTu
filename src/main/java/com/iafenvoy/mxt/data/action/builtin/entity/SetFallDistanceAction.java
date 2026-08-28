package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.NonNull;

public record SetFallDistanceAction(float distance) implements EntityAction {
    public static final MapCodec<SetFallDistanceAction> CODEC = Codec.FLOAT.fieldOf("distance").xmap(SetFallDistanceAction::new, SetFallDistanceAction::distance);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        ctx.entity().fallDistance = this.distance;
    }

    @Override
    public @NonNull MapCodec<SetFallDistanceAction> codec() {
        return CODEC;
    }
}
