package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.CultivateConditions;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * What one stored value <em>is</em>, beyond the number it holds: the aura it carries and how it is used up, the
 * realm chain it opens, its passive regeneration and its conversions to and from cultivation progress.
 * <p>
 * A {@link Resource} is the value itself - bounds, icon, bars, colour - and this is the behaviour laid on one of
 * them, one to one, so a resource is either a plain counter or an aura. The name is the point: what an aura is
 * is decided here ({@code aura_type} marks the element, {@code burst_amount} how much a spirit burst carries),
 * and the ambient aura of the world ({@code mxt:aura_zone}, {@code mxt:block_aura}) is a separate thing that
 * happens to share the word.
 * <p>
 * Cultivation proper - realms, breakthrough, progress - is the process this file lets a value take part in; it
 * is not what this file is. That distinction is why the registry is {@code mxt:aura} and the state attachment a
 * player carries stays {@code mxt:cultivation}.
 */
public record Aura(Holder<Resource> resource, Optional<Holder<RealmStage>> firstRealm,
                   NumberProvider startExp, CultivateConditions startCultivateConditions,
                   ResourceConversion cultivationToResource, ResourceConversion resourceToCultivation,
                   NumberProvider regen, Optional<Holder<Element>> auraType, NumberProvider burstAmount,
                   EntityCondition useCondition, boolean showCultivationInfo) {
    public static final Codec<Holder<Aura>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.AURA);
    public static final Codec<Aura> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(Aura::resource),
            RealmStage.CODEC.optionalFieldOf("first_realm").forGetter(Aura::firstRealm),
            NumberProvider.CODEC.optionalFieldOf("start_exp", new Constant(0.0D)).forGetter(Aura::startExp),
            CultivateConditions.CODEC.optionalFieldOf("start_cultivate_conditions", CultivateConditions.EMPTY).forGetter(Aura::startCultivateConditions),
            ResourceConversion.CODEC.optionalFieldOf("cultivation_to_resource", ResourceConversion.DEFAULT).forGetter(Aura::cultivationToResource),
            ResourceConversion.CODEC.optionalFieldOf("resource_to_cultivation", ResourceConversion.DEFAULT).forGetter(Aura::resourceToCultivation),
            NumberProvider.CODEC.optionalFieldOf("regen", new Constant(0.0D)).forGetter(Aura::regen),
            Element.CODEC.optionalFieldOf("aura_type").forGetter(Aura::auraType),
            NumberProvider.CODEC.optionalFieldOf("burst_amount", new Constant(0.0D)).forGetter(Aura::burstAmount),
            EntityCondition.optionalCodec("use_condition").forGetter(Aura::useCondition),
            Codec.BOOL.optionalFieldOf("show_cultivation_info", true).forGetter(Aura::showCultivationInfo)
    ).apply(i, Aura::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Aura>> STREAM_CODEC = ByteBufCodecs.holderRegistry(MxtResourceKeys.AURA);

    @Override
    public @NonNull String toString() {
        return "Aura[resource=" + HolderHelper.id(this.resource) + ", first_realm="
                + this.firstRealm.map(HolderHelper::id).orElse(HolderHelper.EMPTY) + "]";
    }

    /**
     * One directional conversion between cultivation progress and the stored value.
     * The per-tick limit applies to the consumed source value before the multiplier.
     */
    public record ResourceConversion(NumberProvider multiplier, NumberProvider maxPerTick) {
        public static final ResourceConversion DEFAULT = new ResourceConversion(new Constant(1.0D), new Constant(1.0D));
        public static final Codec<ResourceConversion> CODEC = RecordCodecBuilder.create(i -> i.group(
                NumberProvider.CODEC.optionalFieldOf("multiplier", new Constant(1.0D)).forGetter(ResourceConversion::multiplier),
                NumberProvider.CODEC.optionalFieldOf("max_per_tick", new Constant(1.0D)).forGetter(ResourceConversion::maxPerTick)
        ).apply(i, ResourceConversion::new));
    }
}
