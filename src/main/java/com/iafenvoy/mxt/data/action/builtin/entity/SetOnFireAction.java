package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record SetOnFireAction(int ticks) implements EntityAction {
    public static final MapCodec<SetOnFireAction> CODEC = Codec.INT.fieldOf("ticks").xmap(SetOnFireAction::new, SetOnFireAction::ticks);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        ctx.entity().setRemainingFireTicks(this.ticks);
    }

    @Override
    public @NonNull MapCodec<SetOnFireAction> codec() {
        return CODEC;
    }
}
