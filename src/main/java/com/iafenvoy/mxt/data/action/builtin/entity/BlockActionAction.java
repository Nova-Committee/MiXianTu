package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/**
 * Runs a block action at the acting entity's block position.
 */
public record BlockActionAction(BlockAction action) implements EntityAction {
    public static final MapCodec<BlockActionAction> CODEC = BlockAction.CODEC.fieldOf("action").xmap(BlockActionAction::new, BlockActionAction::action);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        this.action.execute(entity.level(), entity.blockPosition(), ctx);
    }

    @Override
    public MapCodec<BlockActionAction> codec() {
        return CODEC;
    }
}
