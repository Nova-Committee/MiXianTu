package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.api.QualityProvider;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Temperature rating of one furnace wall. The furnace limit is the minimum over the wall cells, never an average.
 * {@code quality} is the tier a wall block of this material starts on, which several materials sharing one block
 * cannot state by item.
 */
public record AlchemyWallMaterial(Component name, Component description, Optional<Holder<ItemQuality>> quality,
                                  double maxTemperature) implements NamedDefinition, QualityProvider {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ALCHEMY_WALL_MATERIAL.identifier());
    public static final Codec<Holder<AlchemyWallMaterial>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ALCHEMY_WALL_MATERIAL);
    public static final Codec<AlchemyWallMaterial> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(AlchemyWallMaterial::name),
            ContextNameCodec.description(CATEGORY).forGetter(AlchemyWallMaterial::description),
            ItemQuality.CODEC.optionalFieldOf("quality").forGetter(AlchemyWallMaterial::quality),
            Codec.DOUBLE.validate(AlchemyWallMaterial::positive).fieldOf("max_temperature").forGetter(AlchemyWallMaterial::maxTemperature)
    ).apply(i, AlchemyWallMaterial::new));

    @Override
    public Optional<Holder<ItemQuality>> defaultQuality() {
        return this.quality;
    }

    private static DataResult<Double> positive(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> "Wall max_temperature must be a finite positive number");
    }
}
