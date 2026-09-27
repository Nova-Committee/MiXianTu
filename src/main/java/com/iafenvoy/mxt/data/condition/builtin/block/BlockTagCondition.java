package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;

/**
 * Matches a block position against a vanilla or datapack block tag.
 */
public record BlockTagCondition(TagKey<Block> tag) implements BlockCondition {
    public static final MapCodec<BlockTagCondition> CODEC = TagKey.hashedCodec(Registries.BLOCK).fieldOf("tag").xmap(BlockTagCondition::new, BlockTagCondition::tag);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return ctx.level().getBlockState(ctx.pos()).is(this.tag);
    }

    @Override
    public @NonNull MapCodec<BlockTagCondition> codec() {
        return CODEC;
    }
}
