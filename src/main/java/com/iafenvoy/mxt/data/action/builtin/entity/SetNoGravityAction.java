package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record SetNoGravityAction(boolean noGravity) implements EntityAction {
    public static final MapCodec<SetNoGravityAction> CODEC = Codec.BOOL.optionalFieldOf("no_gravity", true).xmap(SetNoGravityAction::new, SetNoGravityAction::noGravity);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        ctx.entity().setNoGravity(this.noGravity);
    }

    @Override
    public @NonNull MapCodec<SetNoGravityAction> codec() {
        return CODEC;
    }
}
