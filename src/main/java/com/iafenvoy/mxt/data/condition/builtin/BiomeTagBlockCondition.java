package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Matches the biome at a block position against a datapack biome tag.
 */
public record BiomeTagBlockCondition(TagKey<Biome> tag) implements BlockCondition {
    public static final MapCodec<BiomeTagBlockCondition> CODEC = TagKey.hashedCodec(Registries.BIOME).fieldOf("tag").xmap(BiomeTagBlockCondition::new, BiomeTagBlockCondition::tag);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return level.getBiome(pos).is(this.tag);
    }

    @Override
    public MapCodec<BiomeTagBlockCondition> codec() {
        return CODEC;
    }
}
