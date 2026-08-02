package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.cultivation.RealmStage;
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
public record Resource(NumberProvider defaultValue, NumberProvider min, NumberProvider max, NumberProvider regen,
                       Optional<Holder<ResourceBar>> defaultBar, Optional<Holder<RealmStage>> firstRealm) {
    public static final Codec<Resource> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("default_value").forGetter(Resource::defaultValue),
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(Resource::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(Resource::max),
            NumberProvider.CODEC.optionalFieldOf("regen", new Constant(0.0D)).forGetter(Resource::regen),
            ResourceBar.CODEC.optionalFieldOf("default_bar").forGetter(Resource::defaultBar),
            RealmStage.CODEC.optionalFieldOf("first_realm").forGetter(Resource::firstRealm)
    ).apply(instance, Resource::new));
    public static final Codec<Holder<Resource>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.RESOURCE);
}
