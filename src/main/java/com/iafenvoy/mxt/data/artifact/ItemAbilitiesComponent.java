package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;

/**
 * Explicit abilities granted while an artifact is equipped or otherwise active. The written form is unchanged by the
 * holder codec: one registry id per entry.
 */
public record ItemAbilitiesComponent(List<Holder<Ability>> abilities) {
    public static final Codec<ItemAbilitiesComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(ItemAbilitiesComponent::abilities)
    ).apply(i, ItemAbilitiesComponent::new));

}
