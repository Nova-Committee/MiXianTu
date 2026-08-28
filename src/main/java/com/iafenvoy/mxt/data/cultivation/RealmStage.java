package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ParticleEffect;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * One named realm stage. Conditions and outcomes are resolved by the cultivation runtime.
 */
public record RealmStage(Holder<Resource> resource, NumberProvider auraShareWeight, EntityCondition cultivateCondition,
                         Optional<Holder<RealmStage>> nextRealm,
                         NumberProvider progressThreshold, List<EntityCondition> upgradeConditions,
                         List<AttributeEntry> passiveModifiers, List<ResourceCost> breakthroughCosts,
                         List<Either<Holder<Ability>, TagKey<Ability>>> abilityRequirements,
                         Optional<Holder<Tribulation>> tribulation, Optional<ParticleEffect> breakthroughParticle,
                         EntityAction successAction, EntityAction failAction) {
    public static final Codec<Holder<RealmStage>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.REALM_STAGE);
    public static final Codec<RealmStage> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(RealmStage::resource),
            NumberProvider.CODEC.optionalFieldOf("aura_share_weight", new Constant(1.0D)).forGetter(RealmStage::auraShareWeight),
            EntityCondition.optionalCodec("cultivate_condition").forGetter(RealmStage::cultivateCondition),
            RegistryFixedCodec.create(MxtResourceKeys.REALM_STAGE).optionalFieldOf("next_realm").forGetter(RealmStage::nextRealm),
            NumberProvider.CODEC.fieldOf("progress_threshold").forGetter(RealmStage::progressThreshold),
            AutoIgnoreListCodec.create(EntityCondition.SINGLE_CODEC).optionalFieldOf("upgrade_conditions", List.of()).forGetter(RealmStage::upgradeConditions),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(RealmStage::passiveModifiers),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(RealmStage::breakthroughCosts),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability_requirements", List.of()).forGetter(RealmStage::abilityRequirements),
            Tribulation.CODEC.optionalFieldOf("tribulation").forGetter(RealmStage::tribulation),
            ParticleEffect.CODEC.optionalFieldOf("breakthrough_particle").forGetter(RealmStage::breakthroughParticle),
            EntityAction.optionalCodec("success_action").forGetter(RealmStage::successAction),
            EntityAction.optionalCodec("fail_action").forGetter(RealmStage::failAction)
    ).apply(i, RealmStage::new));

    /**
     * Next-realm links are holder references, so diagnostic output must remain shallow.
     */
    @Override
    public @NonNull String toString() {
        return "RealmStage[resource=" + HolderHelper.id(this.resource) + ", hasNextRealm=" + this.nextRealm.isPresent()
                + ", upgradeConditions=" + this.upgradeConditions.size() + ", costs=" + this.breakthroughCosts.size()
                + ", abilityRequirements=" + this.abilityRequirements.size() + ", hasTribulation=" + this.tribulation.isPresent() + "]";
    }
}
