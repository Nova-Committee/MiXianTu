package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

/**
 * Serialized contract creature retained by a spirit beast bag.
 */
public record SpiritBeastData(Optional<CompoundTag> entity) {
    public static final SpiritBeastData EMPTY = new SpiritBeastData(Optional.empty());
    public static final Codec<SpiritBeastData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CompoundTag.CODEC.optionalFieldOf("entity").forGetter(SpiritBeastData::entity)
    ).apply(instance, SpiritBeastData::new));
}
