package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record SequenceBlockAction(List<BlockAction> actions) implements BlockAction {
    public static final MapCodec<SequenceBlockAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBlockAction::new, SequenceBlockAction::actions);

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        this.actions.forEach(action -> action.execute(ctx.level(), ctx.pos(), ctx));
    }

    @Override
    public @NonNull MapCodec<SequenceBlockAction> codec() {
        return CODEC;
    }
}
