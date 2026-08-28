package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;

public record SetBlockAction(Block block) implements BlockAction {
    public static final MapCodec<SetBlockAction> CODEC = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").xmap(SetBlockAction::new, SetBlockAction::block);

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        // Runtime actions must never load or mutate remote chunks, especially while world generation is active.
        if (level.isClientSide() || !level.hasChunkAt(pos)) return;
        level.setBlock(pos, this.block.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public @NonNull MapCodec<SetBlockAction> codec() {
        return CODEC;
    }
}
