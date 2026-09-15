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
 *
 * <p>{@code absorbed} marks an emitter that stands inside an active formation. Such a block supplies
 * the formation instead of the environment: it is left out of the shared stock and of the spatially
 * weighted view, and its aura is counted separately so the formation can spend it. The flag is decided
 * when the chunk's block aura is rebuilt, because the shared stock subtracts the whole chunk aggregate
 * and filtering only at query time would let the absorbed aura leak back into the environment.</p>
 */
public record BlockAuraContribution(BlockPos position, Map<Holder<Resource>, AuraValue> aura, boolean absorbed) {
    public static final Codec<BlockAuraContribution> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("position").forGetter(BlockAuraContribution::position),
            AuraValue.MAP_CODEC.fieldOf("aura").forGetter(BlockAuraContribution::aura),
            Codec.BOOL.optionalFieldOf("absorbed", false).forGetter(BlockAuraContribution::absorbed)
    ).apply(i, BlockAuraContribution::new));

    public BlockAuraContribution {
        position = position.immutable();
    }
}
