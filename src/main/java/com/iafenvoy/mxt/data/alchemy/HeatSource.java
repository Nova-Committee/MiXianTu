package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Heat of one or more blocks: the ceiling a furnace may reach and how fast it climbs. The furnace reads the block in
 * its bottom centre cell only. Overlapping definitions resolve by {@code priority} - the highest wins, and only a
 * tie falls back to registry order, the same rule the item matchers use. A block implementing
 * {@code com.iafenvoy.mxt.api.AlchemyHeatSource} overrides its entry here.
 */
public record HeatSource(List<Either<Holder<Block>, TagKey<Block>>> blocks, double maxTemperature,
                         double heatingPerTick, int priority) {
    private static final Codec<Double> POSITIVE = Codec.DOUBLE.validate(HeatSource::positive);
    public static final Codec<HeatSource> DIRECT_CODEC = RecordCodecBuilder.<HeatSource>create(i -> i.group(
            RegistryCodecs.holderOrTagList(Registries.BLOCK).fieldOf("blocks").forGetter(HeatSource::blocks),
            POSITIVE.fieldOf("max_temperature").forGetter(HeatSource::maxTemperature),
            POSITIVE.fieldOf("heating_per_tick").forGetter(HeatSource::heatingPerTick),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(HeatSource::priority)
    ).apply(i, HeatSource::new)).validate(HeatSource::validate);

    private static DataResult<HeatSource> validate(HeatSource definition) {
        if (definition.blocks().isEmpty()) return DataResult.error(() -> "heat_source blocks must not be empty");
        return DataResult.success(definition);
    }

    private static DataResult<Double> positive(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> "Heat source values must be finite positive numbers");
    }
}
