package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.data.cultivation.Element;
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

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;

/**
 * Per-block aura contribution, accumulated and cached for each loaded chunk.
 */
public record BlockAura(List<Either<Holder<Block>, TagKey<Block>>> blocks, double auraPerBlock,
                        double regenPerTickPerBlock, Object2DoubleMap<Holder<Element>> elementAuraPerBlock,
                        List<Identifier> auraKinds) {
    public static final Codec<BlockAura> CODEC = RecordCodecBuilder.<BlockAura>create(instance -> instance.group(
            RegistryCodecs.holderOrTagList(Registries.BLOCK).fieldOf("blocks").forGetter(BlockAura::blocks),
            Codec.DOUBLE.optionalFieldOf("aura_per_block", 0.0D).forGetter(BlockAura::auraPerBlock),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick_per_block", 0.0D).forGetter(BlockAura::regenPerTickPerBlock),
            CollectionCodecs.doubleMap(Element.CODEC).optionalFieldOf("element_aura_per_block", Object2DoubleMaps.emptyMap()).forGetter(BlockAura::elementAuraPerBlock),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(BlockAura::auraKinds)
    ).apply(instance, BlockAura::new)).validate(BlockAura::validate);

    private static DataResult<BlockAura> validate(BlockAura definition) {
        if (definition.blocks().isEmpty()) return DataResult.error(() -> "blocks must not be empty");
        if (!finite(definition.auraPerBlock()) || !finite(definition.regenPerTickPerBlock())
                || definition.elementAuraPerBlock().values().doubleStream().anyMatch(value -> !finite(value))) {
            return DataResult.error(() -> "Block aura values must be finite");
        }
        return DataResult.success(definition);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
