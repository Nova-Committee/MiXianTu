package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record BreakBlockAction(boolean drop) implements BlockAction {
    public static final MapCodec<BreakBlockAction> CODEC = Codec.BOOL.optionalFieldOf("drop", true).xmap(BreakBlockAction::new, BreakBlockAction::drop);

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        ctx.level().destroyBlock(ctx.pos(), this.drop);
    }

    @Override
    public @NonNull MapCodec<BreakBlockAction> codec() {
        return CODEC;
    }
}
