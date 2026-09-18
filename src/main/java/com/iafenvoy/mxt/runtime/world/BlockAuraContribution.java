package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * The contribution of one datapack-matched block at a concrete position; the chunk attachment keeps a merged
 * value for its shared stock, while this position-aware view resolves concentration at a point. {@code absorbed}
 * marks an emitter inside an active formation, which supplies the formation instead and is therefore left out
 * of the shared stock and of the weighted view, set when the chunk is rebuilt.
 */
public record BlockAuraContribution(BlockPos position, Map<Holder<Aura>, AuraValue> aura, boolean absorbed) {
    public static final Codec<BlockAuraContribution> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("position").forGetter(BlockAuraContribution::position),
            AuraValue.MAP_CODEC.fieldOf("aura").forGetter(BlockAuraContribution::aura),
            Codec.BOOL.optionalFieldOf("absorbed", false).forGetter(BlockAuraContribution::absorbed)
    ).apply(i, BlockAuraContribution::new));

    public BlockAuraContribution {
        position = position.immutable();
    }
}
