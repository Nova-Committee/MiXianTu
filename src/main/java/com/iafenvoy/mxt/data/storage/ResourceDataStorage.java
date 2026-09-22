package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.data.resource.Resource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * State tied to one resource: the resource names which one, so two of these in one host track two different
 * resources, and {@code amount} is what is kept for it.
 */
public record ResourceDataStorage(Holder<Resource> resource, Optional<Double> amount) implements DataStorage {
    public static final MapCodec<ResourceDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceDataStorage::resource),
            Codec.DOUBLE.optionalFieldOf("amount").forGetter(ResourceDataStorage::amount)
    ).apply(i, ResourceDataStorage::new));

    @Override
    public MapCodec<ResourceDataStorage> codec() {
        return CODEC;
    }

    public ResourceDataStorage withAmount(double value) {
        return new ResourceDataStorage(this.resource, Optional.of(value));
    }
}