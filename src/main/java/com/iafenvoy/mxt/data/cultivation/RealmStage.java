package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * One named realm stage. Conditions and outcomes are resolved by the cultivation runtime.
 */
public record RealmStage(Holder<Resource> resource, Optional<Holder<RealmStage>> nextRealm,
                         NumberProvider progressThreshold, List<EntityCondition> upgradeConditions,
                         List<AttributeModifier> passiveModifiers, List<ResourceCost> breakthroughCosts,
                         List<Either<Holder<Ability>, TagKey<Ability>>> abilityRequirements,
                         Optional<Holder<Tribulation>> tribulation, EntityAction successAction,
                         EntityAction failAction) {
    public static final Codec<Holder<RealmStage>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.REALM_STAGE);
    public static final Codec<RealmStage> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Resource.CODEC.fieldOf("resource").forGetter(RealmStage::resource),
            RegistryFixedCodec.create(MxtRegistryKeys.REALM_STAGE).optionalFieldOf("next_realm").forGetter(RealmStage::nextRealm),
            NumberProvider.CODEC.fieldOf("progress_threshold").forGetter(RealmStage::progressThreshold),
            AutoIgnoreListCodec.create(EntityCondition.SINGLE_CODEC).optionalFieldOf("upgrade_conditions", List.of()).forGetter(RealmStage::upgradeConditions),
            AttributeModifier.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(RealmStage::passiveModifiers),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(RealmStage::breakthroughCosts),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("ability_requirements", List.of()).forGetter(RealmStage::abilityRequirements),
            Tribulation.CODEC.optionalFieldOf("tribulation").forGetter(RealmStage::tribulation),
            EntityAction.CODEC.optionalFieldOf("success_action", NoOpAction.INSTANCE).forGetter(RealmStage::successAction),
            EntityAction.CODEC.optionalFieldOf("fail_action", NoOpAction.INSTANCE).forGetter(RealmStage::failAction)
    ).apply(instance, RealmStage::new));
}
