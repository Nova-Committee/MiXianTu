package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.resource.ResourceDefinition;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * One named realm stage. Conditions and outcomes are resolved by the cultivation runtime.
 */
public record RealmStageDefinition(Holder<ResourceDefinition> resource,
                                   Optional<Holder<RealmStageDefinition>> nextRealm,
                                   NumberProvider progressThreshold,
                                   List<EntityCondition> upgradeConditions,
                                   List<AttributeModifierDefinition> passiveModifiers,
                                   List<ResourceCost> breakthroughCosts,
                                   List<Either<Holder<AbilityDefinition>, TagKey<AbilityDefinition>>> abilityRequirements,
                                   Optional<Holder<TribulationDefinition>> tribulation,
                                   Optional<Identifier> successBehavior, Optional<Identifier> failBehavior) {
    public static final Codec<Holder<RealmStageDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.REALM_STAGE);
    public static final Codec<RealmStageDefinition> CODEC = RecordCodecBuilder.<RealmStageDefinition>create(instance -> instance.group(
            ResourceDefinition.HOLDER_CODEC.fieldOf("resource").forGetter(RealmStageDefinition::resource),
            HOLDER_CODEC.optionalFieldOf("next_realm").forGetter(RealmStageDefinition::nextRealm),
            NumberProvider.CODEC.fieldOf("progress_threshold").forGetter(RealmStageDefinition::progressThreshold),
            AutoIgnoreListCodec.create(EntityCondition.SINGLE_CODEC).optionalFieldOf("upgrade_conditions", List.of()).forGetter(RealmStageDefinition::upgradeConditions),
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(RealmStageDefinition::passiveModifiers),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(RealmStageDefinition::breakthroughCosts),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("ability_requirements", List.of()).forGetter(RealmStageDefinition::abilityRequirements),
            TribulationDefinition.HOLDER_CODEC.optionalFieldOf("tribulation").forGetter(RealmStageDefinition::tribulation),
            Identifier.CODEC.optionalFieldOf("on_success_behavior").forGetter(RealmStageDefinition::successBehavior),
            Identifier.CODEC.optionalFieldOf("on_fail_behavior").forGetter(RealmStageDefinition::failBehavior)
    ).apply(instance, RealmStageDefinition::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR,
            new Reference("on_success_behavior", value.successBehavior),
            new Reference("on_fail_behavior", value.failBehavior)));

}
