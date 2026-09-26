package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Applies an entity action to the target of a bi-entity interaction.
 */
public record TargetAction(EntityAction action) implements BiEntityAction {
    public static final MapCodec<TargetAction> CODEC = EntityAction.CODEC.fieldOf("action").xmap(TargetAction::new, TargetAction::action);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        this.action.execute(ctx.target(), ctx);
    }

    @Override
    public @NonNull MapCodec<TargetAction> codec() {
        return CODEC;
    }
}
