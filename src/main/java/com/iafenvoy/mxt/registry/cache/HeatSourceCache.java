package com.iafenvoy.mxt.registry.cache;

import com.google.common.collect.Maps;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.RegistryPrecedence;
import com.iafenvoy.saturn.registry.cache.RegistryCache;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class HeatSourceCache extends RegistryCache<Map<Block, HeatSource>> {
    private static final Comparator<Reference<HeatSource>> ORDER = RegistryPrecedence.order(HeatSource::priority);
    private final Map<Block, Reference<HeatSource>> data = new LinkedHashMap<>();
    private final Map<Block, HeatSource> values = Maps.transformValues(this.data, Reference::value);

    @Override
    protected void build(RegistryAccess access) {
        access.lookupOrThrow(MxtResourceKeys.HEAT_SOURCE).listElements().sorted(ORDER).forEach(source ->
                RegistryCodecs.resolve(source.value().blocks(), BuiltInRegistries.BLOCK)
                        .forEach(block -> this.data.putIfAbsent(block.value(), source)));
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public Map<Block, HeatSource> get() {
        return this.values;
    }

    @Nullable
    public HeatSource get(Block key) {
        Reference<HeatSource> definition = this.definition(key);
        return definition == null ? null : definition.value();
    }

    @Nullable
    public Reference<HeatSource> definition(Block key) {
        return this.data.get(key);
    }
}
