package com.iafenvoy.mxt.data.secretrealm.builtin;

import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Copies a pre-built level from {@code <server directory>/mxt_secret_realm/<template>/} into the instance before it
 * loads, and takes the terrain from the named stem.
 */
public record TemplateGeneration(String template, ResourceKey<LevelStem> stem) implements SecretRealmGeneration {
    public static final MapCodec<TemplateGeneration> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("template").forGetter(TemplateGeneration::template),
            ResourceKey.codec(Registries.LEVEL_STEM).fieldOf("stem").forGetter(TemplateGeneration::stem)
    ).apply(i, TemplateGeneration::new));

    @Override
    public MapCodec<TemplateGeneration> codec() {
        return CODEC;
    }
}
