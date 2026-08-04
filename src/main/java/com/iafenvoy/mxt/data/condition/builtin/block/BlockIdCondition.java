package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public record BlockIdCondition(Block block) implements BlockCondition {
    public static final MapCodec<BlockIdCondition> CODEC = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").xmap(BlockIdCondition::new, BlockIdCondition::block);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return level.getBlockState(pos).is(this.block);
    }

    @Override
    public MapCodec<BlockIdCondition> codec() {
        return CODEC;
    }
}
