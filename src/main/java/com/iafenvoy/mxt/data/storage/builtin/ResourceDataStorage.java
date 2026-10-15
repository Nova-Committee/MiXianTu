package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A number kept under one resource: {@code resource} names which one and {@code amount} is what is kept for it. The
 * reference is left empty by the declaration a type lists this kind by, and is carried by whatever writes a value.
 */
public final class ResourceDataStorage extends DataStorage {
    // The declaration entry a type lists this kind by; the reference and the amount come from whatever writes it.
    public static final ResourceDataStorage INSTANCE = new ResourceDataStorage(Optional.empty(), OptionalDouble.empty());
    public static final MapCodec<ResourceDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.optionalFieldOf("resource").forGetter(ResourceDataStorage::resource),
            OptionalCodecs.doubleCodec("amount").forGetter(ResourceDataStorage::amount)
    ).apply(i, ResourceDataStorage::new));
    private final Optional<Holder<Resource>> resource;
    private OptionalDouble amount;

    private ResourceDataStorage(Optional<Holder<Resource>> resource, OptionalDouble amount) {
        this.resource = resource;
        this.amount = amount;
    }

    @Override
    public MapCodec<ResourceDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new ResourceDataStorage(this.resource, this.amount);
    }

    public Optional<Holder<Resource>> resource() {
        return this.resource;
    }

    public OptionalDouble amount() {
        return this.amount;
    }

    public void setAmount(double value) {
        this.amount = OptionalDouble.of(value);
        this.markDirty();
    }

    public void clear() {
        this.amount = OptionalDouble.empty();
        this.markDirty();
    }
}
