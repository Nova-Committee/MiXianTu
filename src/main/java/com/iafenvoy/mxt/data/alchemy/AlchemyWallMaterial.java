package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * Temperature rating of one furnace wall. The furnace limit is the minimum of the 22 placed walls, never an average.
 */
public record AlchemyWallMaterial(Component name, Component description, double maxTemperature) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ALCHEMY_WALL_MATERIAL.identifier());
    public static final Codec<Holder<AlchemyWallMaterial>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ALCHEMY_WALL_MATERIAL);
    public static final Codec<AlchemyWallMaterial> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(AlchemyWallMaterial::name),
            ContextNameCodec.description(CATEGORY).forGetter(AlchemyWallMaterial::description),
            Codec.DOUBLE.validate(AlchemyWallMaterial::positive).fieldOf("max_temperature").forGetter(AlchemyWallMaterial::maxTemperature)
    ).apply(i, AlchemyWallMaterial::new));

    private static DataResult<Double> positive(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> "Wall max_temperature must be a finite positive number");
    }
}
