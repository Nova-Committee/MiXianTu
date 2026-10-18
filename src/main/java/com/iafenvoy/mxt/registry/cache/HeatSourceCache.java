package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class HeatSourceCache extends RegistryCache<Map<Block, HeatSource>> {
    private final Map<Block, HeatSource> data = new LinkedHashMap<>();

    @Override
    protected void build(RegistryAccess access) {
        for (HeatSource source : access.lookupOrThrow(MxtResourceKeys.HEAT_SOURCE))
            for (Holder<Block> holder : RegistryCodecs.resolve(source.blocks(), BuiltInRegistries.BLOCK).toList()) {
                HeatSource existing = this.data.get(holder.value());
                if (existing == null || source.priority() > existing.priority())
                    this.data.put(holder.value(), source);
            }
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public Map<Block, HeatSource> get() {
        return this.data;
    }

    @Nullable
    public HeatSource get(Block key) {
        return this.data.get(key);
    }
}
