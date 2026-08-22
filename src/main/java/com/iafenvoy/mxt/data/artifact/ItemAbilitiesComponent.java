package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Explicit ability IDs granted while an artifact is equipped or otherwise active.
 */
public record ItemAbilitiesComponent(List<Identifier> abilities) {
    public static final Codec<ItemAbilitiesComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(ItemAbilitiesComponent::abilities)
    ).apply(i, ItemAbilitiesComponent::new));

}
