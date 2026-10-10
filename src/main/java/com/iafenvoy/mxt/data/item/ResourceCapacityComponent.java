package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;

/**
 * How much of each resource one stack holds, written by whoever hands the container out. A resource with no entry
 * here has no room at all: an unwritten cap is a container that stores nothing, because a resource is a number with
 * no natural maximum of its own.
 */
public record ResourceCapacityComponent(Object2DoubleMap<Holder<Resource>> values) {
    public static final Codec<ResourceCapacityComponent> CODEC = CollectionCodecs.doubleMap(Resource.CODEC).xmap(ResourceCapacityComponent::new, ResourceCapacityComponent::values);
    public static final ResourceCapacityComponent EMPTY = new ResourceCapacityComponent(Object2DoubleMaps.emptyMap());

    public ResourceCapacityComponent(Object2DoubleMap<Holder<Resource>> values) {
        this.values = new Object2DoubleOpenHashMap<>();
        values.forEach((resource, value) -> {
            if (resource == null || !Double.isFinite(value) || value < 0.0D)
                throw new IllegalArgumentException("Resource capacities must be finite and non-negative");
            if (value > 0.0D) this.values.put(resource, value);
        });
    }

    // No entry and a written zero are the same answer, which is what makes "no cap" and "cannot store this" one rule.
    public double capacityOf(Holder<Resource> resource) {
        return this.values.getOrDefault(resource, 0.0D);
    }
}
