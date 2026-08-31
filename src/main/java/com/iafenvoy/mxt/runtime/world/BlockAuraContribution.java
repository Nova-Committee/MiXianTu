package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.resource.Resource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * The contribution of one datapack-matched block at a concrete position.
 * The chunk attachment still keeps a merged value for its shared stock, while
 * this position-aware view is used when resolving concentration at a point.
 */
public record BlockAuraContribution(BlockPos position, Map<Holder<Resource>, AuraValue> aura) {
    public static final Codec<BlockAuraContribution> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("position").forGetter(BlockAuraContribution::position),
            AuraValue.MAP_CODEC.fieldOf("aura").forGetter(BlockAuraContribution::aura)
    ).apply(i, BlockAuraContribution::new));

    public BlockAuraContribution {
        position = position.immutable();
    }
}
