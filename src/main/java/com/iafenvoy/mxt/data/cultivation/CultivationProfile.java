package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * The cultivation behaviour of one stored value.
 *
 * <p>A {@link Resource} only stores a number and displays it; everything that turns that number into
 * a cultivation resource - the realm chain it enters, its passive regeneration, how it converts to
 * and from cultivation progress, which aura it carries, whether it can be used and whether it shows
 * up in the cultivation information panel - lives here and points back at the value it describes.</p>
 *
 * <p>The relationship is one to one: one value has at most one profile, which the server cache
 * enforces, so a resource is either a plain counter or a cultivation resource and never both.</p>
 */
public record CultivationProfile(Holder<Resource> resource, Optional<Holder<RealmStage>> firstRealm,
                                 NumberProvider startExp, CultivateConditions startCultivateConditions,
                                 ResourceConversion cultivationToResource, ResourceConversion resourceToCultivation,
                                 NumberProvider regen, Optional<Holder<Element>> auraType, NumberProvider burstAmount,
                                 EntityCondition useCondition, boolean showCultivationInfo) {
    public static final Codec<Holder<CultivationProfile>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CULTIVATION);
    public static final Codec<CultivationProfile> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(CultivationProfile::resource),
            RealmStage.CODEC.optionalFieldOf("first_realm").forGetter(CultivationProfile::firstRealm),
            NumberProvider.CODEC.optionalFieldOf("start_exp", new Constant(0.0D)).forGetter(CultivationProfile::startExp),
            CultivateConditions.CODEC.optionalFieldOf("start_cultivate_conditions", CultivateConditions.EMPTY).forGetter(CultivationProfile::startCultivateConditions),
            ResourceConversion.CODEC.optionalFieldOf("cultivation_to_resource", ResourceConversion.DEFAULT).forGetter(CultivationProfile::cultivationToResource),
            ResourceConversion.CODEC.optionalFieldOf("resource_to_cultivation", ResourceConversion.DEFAULT).forGetter(CultivationProfile::resourceToCultivation),
            NumberProvider.CODEC.optionalFieldOf("regen", new Constant(0.0D)).forGetter(CultivationProfile::regen),
            Element.CODEC.optionalFieldOf("aura_type").forGetter(CultivationProfile::auraType),
            NumberProvider.CODEC.optionalFieldOf("burst_amount", new Constant(0.0D)).forGetter(CultivationProfile::burstAmount),
            EntityCondition.optionalCodec("use_condition").forGetter(CultivationProfile::useCondition),
            Codec.BOOL.optionalFieldOf("show_cultivation_info", true).forGetter(CultivationProfile::showCultivationInfo)
    ).apply(i, CultivationProfile::new));

    @Override
    public @NonNull String toString() {
        return "CultivationProfile[resource=" + HolderHelper.id(this.resource) + ", first_realm="
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
