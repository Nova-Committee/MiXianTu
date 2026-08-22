package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Per-block aura contribution, accumulated and cached for each loaded chunk.
 */
public record BlockAura(List<Either<Holder<Block>, TagKey<Block>>> blocks, Map<Holder<Element>, AuraValue> aura,
                        List<Identifier> auraKinds) {
    public static final Codec<BlockAura> CODEC = RecordCodecBuilder.<BlockAura>create(i -> i.group(
            RegistryCodecs.holderOrTagList(Registries.BLOCK).fieldOf("blocks").forGetter(BlockAura::blocks),
            AuraValue.MAP_CODEC.optionalFieldOf("aura", Map.of()).forGetter(BlockAura::aura),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(BlockAura::auraKinds)
    ).apply(i, BlockAura::new)).validate(BlockAura::validate);

    private static DataResult<BlockAura> validate(BlockAura definition) {
        if (definition.blocks().isEmpty()) return DataResult.error(() -> "blocks must not be empty");
        return DataResult.success(definition);
    }

}
