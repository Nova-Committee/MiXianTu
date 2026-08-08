package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;

/**
 * Portable, resource-agnostic energy storage used by stones, batteries and future artifacts.
 */
public record ResourceContainerData(Object2DoubleMap<Holder<Resource>> values) {
    public static final Codec<ResourceContainerData> CODEC = CollectionCodecs.doubleMap(Resource.CODEC).xmap(ResourceContainerData::new, ResourceContainerData::values);
    public static final ResourceContainerData EMPTY = new ResourceContainerData(Object2DoubleMaps.emptyMap());

    public ResourceContainerData(Object2DoubleMap<Holder<Resource>> values) {
        this.values = new Object2DoubleOpenHashMap<>();
        values.forEach((resource, value) -> {
            if (resource == null || !Double.isFinite(value) || value < 0.0D)
                throw new IllegalArgumentException("Stored resource values must be finite and non-negative");
            if (value > 0.0D) this.values.put(resource, value);
        });
    }

    public ResourceContainerData with(Holder<Resource> resource, double value) {
        Object2DoubleMap<Holder<Resource>> next = new Object2DoubleOpenHashMap<>(this.values);
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Stored resource value must be finite and non-negative");
        if (value == 0.0D) next.removeDouble(resource);
        else next.put(resource, value);
        return new ResourceContainerData(next);
    }
}
