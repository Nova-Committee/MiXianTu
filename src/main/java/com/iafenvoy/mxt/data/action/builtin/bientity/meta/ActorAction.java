package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/**
 * Applies an entity action to the actor of a bi-entity interaction.
 */
public record ActorAction(EntityAction action) implements BiEntityAction {
    public static final MapCodec<ActorAction> CODEC = EntityAction.CODEC.fieldOf("action").xmap(ActorAction::new, ActorAction::action);

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        this.action.execute(actor, context);
    }

    @Override
    public MapCodec<ActorAction> codec() {
        return CODEC;
    }
}
