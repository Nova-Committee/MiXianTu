package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record SequenceBiEntityAction(List<BiEntityAction> actions) implements BiEntityAction {
    public static final MapCodec<SequenceBiEntityAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBiEntityAction::new, SequenceBiEntityAction::actions);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        this.actions.forEach(action -> action.execute(ctx.actor(), ctx.target(), ctx));
    }

    @Override
    public @NonNull MapCodec<SequenceBiEntityAction> codec() {
        return CODEC;
    }
}
