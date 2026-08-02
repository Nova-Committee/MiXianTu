package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.cultivation.RealmStageDefinition;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Datapack definition of an entity resource such as spirit power or soul power.
 */
public record ResourceDefinition(NumberProvider defaultValue, NumberProvider min,
                                 NumberProvider max, NumberProvider regen,
                                 Optional<Holder<ResourceBarDefinition>> defaultBar,
                                 Optional<Holder<RealmStageDefinition>> firstRealm) {
    public static final Codec<Holder<ResourceDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.RESOURCE);
    public static final Codec<ResourceDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("default_value").forGetter(ResourceDefinition::defaultValue),
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(ResourceDefinition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(ResourceDefinition::max),
            NumberProvider.CODEC.optionalFieldOf("regen", new Constant(0.0D)).forGetter(ResourceDefinition::regen),
            ResourceBarDefinition.HOLDER_CODEC.optionalFieldOf("default_bar").forGetter(ResourceDefinition::defaultBar),
            RealmStageDefinition.HOLDER_CODEC.optionalFieldOf("first_realm").forGetter(ResourceDefinition::firstRealm)
    ).apply(instance, ResourceDefinition::new));
}
