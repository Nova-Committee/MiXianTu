package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Map;

/** Persistent aggregate and source positions for one vertical chunk section. */
public record BlockAuraSectionCache(Map<Holder<Resource>, AuraValue> aura,
                                    List<BlockAuraContribution> sources) {
    public static final Codec<BlockAuraSectionCache> CODEC = RecordCodecBuilder.create(i -> i.group(
            AuraValue.MAP_CODEC.optionalFieldOf("aura", Map.of()).forGetter(BlockAuraSectionCache::aura),
            CollectionCodecs.list(BlockAuraContribution.CODEC).optionalFieldOf("sources", List.of()).forGetter(BlockAuraSectionCache::sources)
    ).apply(i, BlockAuraSectionCache::new));
}
