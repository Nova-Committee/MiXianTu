package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record SequenceItemAction(List<ItemAction> actions) implements ItemAction {
    public static final MapCodec<SequenceItemAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceItemAction::new, SequenceItemAction::actions);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        this.actions.forEach(action -> action.execute(ctx.holder(), ctx.stack(), ctx));
    }

    @Override
    public @NonNull MapCodec<SequenceItemAction> codec() {
        return CODEC;
    }
}
