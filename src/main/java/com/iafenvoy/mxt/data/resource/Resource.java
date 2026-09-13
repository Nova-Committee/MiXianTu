package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.HotbarIcon;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
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
 * Datapack definition of a stored value: its bounds and how it is displayed.
 *
 * <p>A resource is a number the mod keeps per entity. It stores nothing about where that number
 * comes from or what it is for; the cultivation behaviour of a value is a separate
 * {@link com.iafenvoy.mxt.data.cultivation.CultivationProfile} that references it, so a resource
 * without a profile is simply a counter.</p>
 */
public record Resource(NumberProvider defaultValue, NumberProvider min, NumberProvider max,
                       Optional<HotbarIcon> icon, int particleColor, List<ResourceBar> bars) {
    public static final Codec<Holder<Resource>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.RESOURCE);
    public static final Codec<Resource> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            NumberProvider.CODEC.fieldOf("default_value").forGetter(Resource::defaultValue),
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(Resource::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(Resource::max),
            HotbarIcon.CODEC.optionalFieldOf("icon").forGetter(Resource::icon),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("particle_color", 0xFFFFFF).forGetter(Resource::particleColor),
            ResourceBar.CODEC.listOf().optionalFieldOf("bars", List.of()).forGetter(Resource::bars)
    ).apply(i, Resource::new));

    @Override
    public @NonNull String toString() {
        return "Resource[bars=" + this.bars.size() + "]";
    }
}
