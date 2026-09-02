package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Runs a block action at the acting entity's block position.
 */
public record BlockActionAction(BlockAction action) implements EntityAction {
    public static final MapCodec<BlockActionAction> CODEC = BlockAction.CODEC.fieldOf("action").xmap(BlockActionAction::new, BlockActionAction::action);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        this.action.execute(entity.level(), entity.blockPosition(), ctx);
    }

    @Override
    public @NonNull MapCodec<BlockActionAction> codec() {
        return CODEC;
    }
}
