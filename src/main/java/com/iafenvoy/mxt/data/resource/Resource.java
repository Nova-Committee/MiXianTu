package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Datapack definition of an entity resource such as spirit power or soul power.
 * Its {@code max} and {@code regen} formulas receive the owning resource chain's
 * realm rank and absorbed aura through {@code ResourceService}.
 */
public record Resource(NumberProvider defaultValue, NumberProvider min, NumberProvider max, NumberProvider regen,
                       ResourceConversion cultivationToResource, ResourceConversion resourceToCultivation,
                       List<ResourceBar> bars, Optional<Holder<RealmStage>> firstRealm) {
    public static final Codec<Holder<Resource>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.RESOURCE);
    public static final Codec<Resource> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("default_value").forGetter(Resource::defaultValue),
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(Resource::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(Resource::max),
            NumberProvider.CODEC.optionalFieldOf("regen", new Constant(0.0D)).forGetter(Resource::regen),
            ResourceConversion.CODEC.optionalFieldOf("cultivation_to_resource", ResourceConversion.DEFAULT).forGetter(Resource::cultivationToResource),
            ResourceConversion.CODEC.optionalFieldOf("resource_to_cultivation", ResourceConversion.DEFAULT).forGetter(Resource::resourceToCultivation),
            ResourceBar.CODEC.listOf().optionalFieldOf("bars", List.of()).forGetter(Resource::bars),
            RealmStage.CODEC.optionalFieldOf("first_realm").forGetter(Resource::firstRealm)
    ).apply(instance, Resource::new));

    @Override
    public @NonNull String toString() {
        return "Resource[bars=" + this.bars.size() + ", first_realm="
                + this.firstRealm.map(HolderHelper::id).orElse(HolderHelper.EMPTY) + "]";
    }

    /**
     * One directional conversion between cultivation progress and an entity resource.
     * The per-tick limit applies to the consumed source value before the multiplier.
     */
    public record ResourceConversion(NumberProvider multiplier, NumberProvider maxPerTick) {
        public static final ResourceConversion DEFAULT = new ResourceConversion(new Constant(1.0D), new Constant(1.0D));
        public static final Codec<ResourceConversion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NumberProvider.CODEC.optionalFieldOf("multiplier", new Constant(1.0D)).forGetter(ResourceConversion::multiplier),
                NumberProvider.CODEC.optionalFieldOf("max_per_tick", new Constant(1.0D)).forGetter(ResourceConversion::maxPerTick)
        ).apply(instance, ResourceConversion::new));
    }
}
