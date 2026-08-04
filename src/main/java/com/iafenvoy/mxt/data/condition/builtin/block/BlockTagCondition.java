package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Matches a block position against a vanilla or datapack block tag.
 */
public record BlockTagCondition(TagKey<Block> tag) implements BlockCondition {
    public static final MapCodec<BlockTagCondition> CODEC = TagKey.hashedCodec(Registries.BLOCK).fieldOf("tag").xmap(BlockTagCondition::new, BlockTagCondition::tag);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return level.getBlockState(pos).is(this.tag);
    }

    @Override
    public MapCodec<BlockTagCondition> codec() {
        return CODEC;
    }
}
