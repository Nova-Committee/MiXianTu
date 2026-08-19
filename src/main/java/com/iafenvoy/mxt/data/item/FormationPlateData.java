package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Selected formation definition for a portable formation controller.
 */
public record FormationPlateData(Optional<Holder<Formation>> formation) {
    public static final FormationPlateData EMPTY = new FormationPlateData(Optional.empty());
    public static final Codec<FormationPlateData> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.FORMATION).optionalFieldOf("formation").forGetter(FormationPlateData::formation)
    ).apply(i, FormationPlateData::new));
}
