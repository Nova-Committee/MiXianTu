package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * The resolved item id for a framework-provided unidentified item.
 */
public record IdentificationData(Identifier result) {
    public static final Codec<IdentificationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("result").forGetter(IdentificationData::result)
    ).apply(instance, IdentificationData::new));
}
