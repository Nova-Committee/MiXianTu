package com.iafenvoy.mxt.data.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Heat of one block: the ceiling a furnace may reach and how fast it climbs. The block is the data map's key, and
 * the furnace reads the block in its bottom centre cell only. Overlapping values resolve by {@code priority} - the
 * highest wins. A block implementing {@code com.iafenvoy.mxt.api.AlchemyHeatSource} overrides its value here.
 */
public record HeatSource(double maxTemperature, double heatingPerTick, int priority) {
    private static final Codec<Double> POSITIVE = Codec.DOUBLE.validate(HeatSource::positive);
    public static final Codec<HeatSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            POSITIVE.fieldOf("max_temperature").forGetter(HeatSource::maxTemperature),
            POSITIVE.fieldOf("heating_per_tick").forGetter(HeatSource::heatingPerTick),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(HeatSource::priority)
    ).apply(i, HeatSource::new));

    private static DataResult<Double> positive(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> "Heat source values must be finite positive numbers");
    }
}
