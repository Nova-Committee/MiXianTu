package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Datapack definition of a stored value: its bounds and how it is displayed. A resource stores nothing about where
 * it comes from or what it is for - that is the job of the {@link com.iafenvoy.mxt.data.aura.Aura} referencing it -
 * so one without a profile is simply a counter.
 */
public record Resource(Component name, Component description, NumberProvider defaultValue, NumberProvider min,
                       NumberProvider max, Optional<IconReference> icon, int particleColor, List<ResourceBar> bars)
        implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.RESOURCE.identifier());
    public static final Codec<Holder<Resource>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.RESOURCE);
    public static final Codec<Resource> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Resource::name),
            ContextNameCodec.description(CATEGORY).forGetter(Resource::description),
            NumberProvider.CODEC.fieldOf("default_value").forGetter(Resource::defaultValue),
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(Resource::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(Resource::max),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Resource::icon),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("particle_color", 0xFFFFFF).forGetter(Resource::particleColor),
            ResourceBar.CODEC.listOf().optionalFieldOf("bars", List.of()).forGetter(Resource::bars)
    ).apply(i, Resource::new));

    @Override
    public @NonNull String toString() {
        return "Resource[bars=" + this.bars.size() + "]";
    }
}
