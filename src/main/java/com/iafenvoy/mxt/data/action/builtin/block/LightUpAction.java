package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.NonNull;

/**
 * Sets a block's vanilla {@code lit} property when it has one.
 */
public enum LightUpAction implements BlockAction {
    INSTANCE;
    public static final MapCodec<LightUpAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT))
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
    }

    @Override
    public @NonNull MapCodec<LightUpAction> codec() {
        return CODEC;
    }
}
