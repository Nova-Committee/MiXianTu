package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.NonNull;

/**
 * Matches the biome at a block position against a datapack biome tag.
 */
public record BiomeTagBlockCondition(TagKey<Biome> tag) implements BlockCondition {
    public static final MapCodec<BiomeTagBlockCondition> CODEC = TagKey.hashedCodec(Registries.BIOME).fieldOf("tag").xmap(BiomeTagBlockCondition::new, BiomeTagBlockCondition::tag);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return ctx.level().getBiome(ctx.pos()).is(this.tag);
    }

    @Override
    public @NonNull MapCodec<BiomeTagBlockCondition> codec() {
        return CODEC;
    }
}
