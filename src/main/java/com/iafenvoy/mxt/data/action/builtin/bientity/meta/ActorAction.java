package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Applies an entity action to the actor of a bi-entity interaction.
 */
public record ActorAction(EntityAction action) implements BiEntityAction {
    public static final MapCodec<ActorAction> CODEC = EntityAction.CODEC.fieldOf("action").xmap(ActorAction::new, ActorAction::action);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        this.action.execute(actor, ctx);
    }

    @Override
    public @NonNull MapCodec<ActorAction> codec() {
        return CODEC;
    }
}
