package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.NonNull;

/**
 * Runs a block action at the block the activation happens in - the acting entity's own, or the place an item
 * cast it from.
 */
public record BlockActionAction(BlockAction action) implements EntityAction {
    public static final MapCodec<BlockActionAction> CODEC = BlockAction.CODEC.fieldOf("action").xmap(BlockActionAction::new, BlockActionAction::action);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        this.action.execute(ctx.entity().level(), BlockPos.containing(ctx.position()), ctx);
    }

    @Override
    public @NonNull MapCodec<BlockActionAction> codec() {
        return CODEC;
    }
}
