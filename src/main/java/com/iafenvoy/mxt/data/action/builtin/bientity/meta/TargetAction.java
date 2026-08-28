package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/**
 * Applies an entity action to the target of a bi-entity interaction.
 */
public record TargetAction(EntityAction action) implements BiEntityAction {
    public static final MapCodec<TargetAction> CODEC = EntityAction.CODEC.fieldOf("action").xmap(TargetAction::new, TargetAction::action);

    @Override
    public void execute(BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        this.action.execute(target, ctx);
    }

    @Override
    public MapCodec<TargetAction> codec() {
        return CODEC;
    }
}
