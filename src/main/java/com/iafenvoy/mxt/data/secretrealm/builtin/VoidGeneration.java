package com.iafenvoy.mxt.data.secretrealm.builtin;

import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Optional;

/**
 * No layers, one biome, and by default no structures - the usual floor for a secret realm furnished entirely from
 * structure templates.
 */
public record VoidGeneration(Optional<ResourceKey<Biome>> biome, Optional<ResourceKey<DimensionType>> dimensionType,
                             boolean structures) implements SecretRealmGeneration {
    public static final MapCodec<VoidGeneration> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome").forGetter(VoidGeneration::biome),
            ResourceKey.codec(Registries.DIMENSION_TYPE).optionalFieldOf("dimension_type").forGetter(VoidGeneration::dimensionType),
            Codec.BOOL.optionalFieldOf("structures", false).forGetter(VoidGeneration::structures)
    ).apply(i, VoidGeneration::new));

    @Override
    public MapCodec<VoidGeneration> codec() {
        return CODEC;
    }
}
