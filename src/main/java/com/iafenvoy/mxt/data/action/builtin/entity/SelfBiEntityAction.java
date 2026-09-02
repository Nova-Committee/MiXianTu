package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Applies a bi-entity action with the actor and target both set to the current entity.
 */
public record SelfBiEntityAction(BiEntityAction action) implements EntityAction {
    public static final MapCodec<SelfBiEntityAction> CODEC = BiEntityAction.CODEC.fieldOf("action").xmap(SelfBiEntityAction::new, SelfBiEntityAction::action);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        this.action.execute(entity, entity, ctx);
    }

    @Override
    public @NonNull MapCodec<SelfBiEntityAction> codec() {
        return CODEC;
    }
}
