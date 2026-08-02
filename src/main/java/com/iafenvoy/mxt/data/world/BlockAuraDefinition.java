package com.iafenvoy.mxt.data.world;

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
public record BlockAuraDefinition(List<Either<Holder<Block>, TagKey<Block>>> blocks, double auraPerBlock,
                                  double regenPerTickPerBlock,
                                  Map<Identifier, Double> elementAuraPerBlock, List<Identifier> environmentTags) {
    public static final Codec<BlockAuraDefinition> CODEC = RecordCodecBuilder.<BlockAuraDefinition>create(instance -> instance.group(
            RegistryCodecs.holderOrTagList(Registries.BLOCK).fieldOf("blocks").forGetter(BlockAuraDefinition::blocks),
            Codec.DOUBLE.optionalFieldOf("aura_per_block", 0.0D).forGetter(BlockAuraDefinition::auraPerBlock),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick_per_block", 0.0D).forGetter(BlockAuraDefinition::regenPerTickPerBlock),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("element_aura_per_block", Map.of()).forGetter(BlockAuraDefinition::elementAuraPerBlock),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(BlockAuraDefinition::environmentTags)
    ).apply(instance, BlockAuraDefinition::new)).validate(BlockAuraDefinition::validate);

    private static DataResult<BlockAuraDefinition> validate(BlockAuraDefinition definition) {
        if (definition.blocks().isEmpty()) return DataResult.error(() -> "blocks must not be empty");
        if (!finite(definition.auraPerBlock()) || !finite(definition.regenPerTickPerBlock())
                || definition.elementAuraPerBlock().values().stream().anyMatch(value -> !finite(value))) {
            return DataResult.error(() -> "Block aura values must be finite");
        }
        return DataResult.success(definition);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
