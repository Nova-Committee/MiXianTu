package com.iafenvoy.mxt.registry.cache;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ReverseMappingCache<K, V> extends RegistryCache<Map<K, Holder<V>>> {
    private final ResourceKey<Registry<V>> registry;
    private final Function<Holder<V>, K> keyMapper;
    private final Map<K, Holder<V>> data = new LinkedHashMap<>();

    public ReverseMappingCache(ResourceKey<Registry<V>> registry, Function<Holder<V>, K> keyMapper) {
        this.registry = registry;
        this.keyMapper = keyMapper;
    }

    @Override
    protected void build(RegistryAccess access) {
        access.lookupOrThrow(this.registry).listElements().forEach(holder -> this.data.put(this.keyMapper.apply(holder), holder));
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public Map<K, Holder<V>> get() {
        return this.data;
    }

    public Optional<Holder<V>> get(K key) {
        return Optional.ofNullable(this.data.get(key));
    }
}
