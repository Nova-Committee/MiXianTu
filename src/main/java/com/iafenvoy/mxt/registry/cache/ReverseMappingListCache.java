package com.iafenvoy.mxt.registry.cache;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class ReverseMappingListCache<K, T> extends RegistryCache<ListMultimap<K, Holder<T>>> {
    private final ResourceKey<Registry<T>> registry;
    private final Function<Holder<T>, List<K>> keyMapper;
    private final ListMultimap<K, Holder<T>> data = LinkedListMultimap.create();

    public ReverseMappingListCache(ResourceKey<Registry<T>> registry, Function<Holder<T>, List<K>> keyMapper) {
        this.registry = registry;
        this.keyMapper = keyMapper;
    }

    @Override
    protected void build(RegistryAccess access) {
        access.lookupOrThrow(this.registry).listElements().forEach(holder -> this.keyMapper.apply(holder).forEach(key -> this.data.put(key, holder)));
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public ListMultimap<K, Holder<T>> get() {
        return this.data;
    }

    @NotNull
    public List<Holder<T>> get(K key) {
        return this.data.get(key);
    }

    public boolean containsKey(K key) {
        return this.data.containsKey(key);
    }

    public Stream<String> suggestions() {
        return this.data.keySet().stream().map(Object::toString).sorted();
    }
}
