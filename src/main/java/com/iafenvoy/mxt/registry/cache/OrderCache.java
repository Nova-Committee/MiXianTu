package com.iafenvoy.mxt.registry.cache;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class OrderCache<T> extends RegistryCache<List<Holder<T>>> {
    private final ResourceKey<Registry<T>> registry;
    private final Comparator<Holder.Reference<T>> comparator;
    private final List<Holder<T>> data = new LinkedList<>();

    public OrderCache(ResourceKey<Registry<T>> registry, Comparator<Holder.Reference<T>> comparator) {
        this.registry = registry;
        this.comparator = comparator;
    }

    @Override
    protected void build(RegistryAccess access) {
        access.lookupOrThrow(this.registry).listElements().sorted(this.comparator).forEach(this.data::add);
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public List<Holder<T>> get() {
        return this.data;
    }

    public Stream<Holder<T>> stream() {
        return this.data.stream();
    }
}
