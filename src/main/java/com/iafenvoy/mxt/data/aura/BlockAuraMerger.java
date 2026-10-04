package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapValueMerger;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blocks accumulate the way the per-chunk aggregate does: a block matched by two entries emits both, so the merge
 * rule is the aggregate's own {@link AuraChunkAttachment#merge}, not "the later value wins". It sits with the value
 * it merges rather than with the runtime that reads the resulting index.
 */
public record BlockAuraMerger() implements DataMapValueMerger<Block, Map<Holder<Aura>, AuraValue>> {
    @Override
    public Map<Holder<Aura>, AuraValue> merge(@NonNull Registry<Block> registry, @NonNull Either<TagKey<Block>, ResourceKey<Block>> first,
                                              Map<Holder<Aura>, AuraValue> firstValue,
                                              @NonNull Either<TagKey<Block>, ResourceKey<Block>> second,
                                              Map<Holder<Aura>, AuraValue> secondValue) {
        Map<Holder<Aura>, AuraValue> merged = new LinkedHashMap<>(firstValue);
        secondValue.forEach((aura, value) -> merged.merge(aura, value, AuraChunkAttachment::merge));
        return Map.copyOf(merged);
    }
}
