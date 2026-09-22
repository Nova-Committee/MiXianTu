package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * The contribution of one datapack-matched block at a concrete position. {@code absorbed} marks an emitter
 * inside an active formation: the formation supplies it instead, so it is left out of both the shared stock and
 * the weighted view. Set when the chunk is rebuilt.
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
