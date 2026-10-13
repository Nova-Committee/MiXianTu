package com.iafenvoy.mxt.data.secretrealm.builtin;

import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Optional;

/**
 * Builds a superflat instance from a preset string. The preset follows the vanilla layer syntax, for example
 * {@code 1*minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains}.
 */
public record FlatGeneration(String preset, Optional<ResourceKey<DimensionType>> dimensionType,
                             boolean structures) implements SecretRealmGeneration {
    public static final MapCodec<FlatGeneration> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("preset").forGetter(FlatGeneration::preset),
            ResourceKey.codec(Registries.DIMENSION_TYPE).optionalFieldOf("dimension_type").forGetter(FlatGeneration::dimensionType),
            Codec.BOOL.optionalFieldOf("structures", true).forGetter(FlatGeneration::structures)
    ).apply(i, FlatGeneration::new));

    @Override
    public MapCodec<FlatGeneration> codec() {
        return CODEC;
    }
}
