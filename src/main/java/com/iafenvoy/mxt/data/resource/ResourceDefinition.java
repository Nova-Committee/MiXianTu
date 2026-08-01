package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Datapack definition of an entity resource such as spirit power or soul power.
 */
public record ResourceDefinition(NumberProvider defaultValue, NumberProvider min,
                                 NumberProvider max, NumberProvider regen, Optional<Identifier> defaultBar) {
    public static final Codec<ResourceDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("default_value").forGetter(ResourceDefinition::defaultValue),
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(ResourceDefinition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(ResourceDefinition::max),
            NumberProvider.CODEC.optionalFieldOf("regen", new Constant(0.0D)).forGetter(ResourceDefinition::regen),
            Identifier.CODEC.optionalFieldOf("default_bar").forGetter(ResourceDefinition::defaultBar)
    ).apply(instance, ResourceDefinition::new));
}
