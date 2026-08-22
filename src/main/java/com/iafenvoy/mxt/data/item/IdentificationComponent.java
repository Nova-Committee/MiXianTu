package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * The resolved item id for a framework-provided unidentified item.
 */
public record IdentificationComponent(Identifier result) {
    public static final Codec<IdentificationComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result").forGetter(IdentificationComponent::result)
    ).apply(i, IdentificationComponent::new));
}
