package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Map;

/**
 * The contribution of one datapack-matched block at a concrete position. {@code absorbedBy} names the formations
 * that absorb this emitter - each by its controller position - so a formation supplies only what stands inside
 * itself; empty means the emitter belongs to the environment. Set when the chunk is rebuilt.
 */
public record BlockAuraContribution(BlockPos position, Map<Holder<Aura>, AuraValue> aura, List<BlockPos> absorbedBy) {
    public static final Codec<BlockAuraContribution> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("position").forGetter(BlockAuraContribution::position),
            AuraValue.MAP_CODEC.fieldOf("aura").forGetter(BlockAuraContribution::aura),
            CollectionCodecs.list(BlockPos.CODEC).lenientOptionalFieldOf("absorbed_by", List.of()).forGetter(BlockAuraContribution::absorbedBy)
    ).apply(i, BlockAuraContribution::new));

    public BlockAuraContribution {
        position = position.immutable();
        absorbedBy = List.copyOf(absorbedBy);
    }

    public boolean absorbed() {
        return !this.absorbedBy.isEmpty();
    }
}
