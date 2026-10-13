package com.iafenvoy.mxt.data.secretrealm.builtin;

import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Uses an already loaded dimension and creates nothing: {@code max_instances} is meaningless here, and the service
 * never destroys that dimension.
 */
public record ExistingGeneration(ResourceKey<Level> dimension) implements SecretRealmGeneration {
    public static final MapCodec<ExistingGeneration> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(ExistingGeneration::dimension)
    ).apply(i, ExistingGeneration::new));

    @Override
    public MapCodec<ExistingGeneration> codec() {
        return CODEC;
    }
}
