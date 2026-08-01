package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Explicit ability IDs granted while an artifact is equipped or otherwise active.
 */
public record ItemAbilitiesData(List<Identifier> abilities) {
    public static final MapCodec<ItemAbilitiesData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(ItemAbilitiesData::abilities)
    ).apply(instance, ItemAbilitiesData::new));
    public static final Codec<ItemAbilitiesData> CODEC = MAP_CODEC.codec();

    public ItemAbilitiesData {
        abilities = List.copyOf(abilities);
    }
}
