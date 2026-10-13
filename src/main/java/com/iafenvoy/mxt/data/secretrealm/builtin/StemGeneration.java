package com.iafenvoy.mxt.data.secretrealm.builtin;

import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Builds the instance from a level stem the data pack declares. The stem is read when an instance is created, not
 * when the definition is decoded, so a realm may name a stem another pack provides.
 */
public record StemGeneration(ResourceKey<LevelStem> stem) implements SecretRealmGeneration {
    public static final MapCodec<StemGeneration> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.LEVEL_STEM).fieldOf("stem").forGetter(StemGeneration::stem)
    ).apply(i, StemGeneration::new));

    @Override
    public MapCodec<StemGeneration> codec() {
        return CODEC;
    }
}
