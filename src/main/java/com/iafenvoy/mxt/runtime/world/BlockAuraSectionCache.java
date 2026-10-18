package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * Persistent aggregate for one vertical chunk section. The per-block positions are not stored: the exact
 * contribution reads them from the live section instead.
 */
public record BlockAuraSectionCache(Map<Holder<Aura>, AuraValue> aura) {
    public static final Codec<BlockAuraSectionCache> CODEC = RecordCodecBuilder.create(i -> i.group(
            AuraValue.MAP_CODEC.lenientOptionalFieldOf("aura", Map.of()).forGetter(BlockAuraSectionCache::aura)
    ).apply(i, BlockAuraSectionCache::new));
}
