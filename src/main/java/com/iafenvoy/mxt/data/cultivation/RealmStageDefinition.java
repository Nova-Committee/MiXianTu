package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * One named realm stage. Conditions and outcomes are resolved by the cultivation runtime.
 */
public record RealmStageDefinition(Optional<Identifier> parent,
                                   NumberProvider progressThreshold,
                                   List<Identifier> breakthroughConditions,
                                   List<AttributeModifierDefinition> passiveModifiers,
                                   List<ResourceCost> breakthroughCosts, List<Identifier> abilityRequirements,
                                   Optional<Identifier> tribulation,
                                   Optional<Identifier> successBehavior, Optional<Identifier> failBehavior) {
    public static final Codec<RealmStageDefinition> CODEC = RecordCodecBuilder.<RealmStageDefinition>create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("parent").forGetter(RealmStageDefinition::parent),
            NumberProvider.CODEC.fieldOf("progress_threshold").forGetter(RealmStageDefinition::progressThreshold),
            Identifier.CODEC.listOf().optionalFieldOf("breakthrough_conditions", List.of()).forGetter(RealmStageDefinition::breakthroughConditions),
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(RealmStageDefinition::passiveModifiers),
            ResourceCost.CODEC.listOf().optionalFieldOf("costs", List.of()).forGetter(RealmStageDefinition::breakthroughCosts),
            Identifier.CODEC.listOf().optionalFieldOf("ability_requirements", List.of()).forGetter(RealmStageDefinition::abilityRequirements),
            Identifier.CODEC.optionalFieldOf("tribulation").forGetter(RealmStageDefinition::tribulation),
            Identifier.CODEC.optionalFieldOf("on_success_behavior").forGetter(RealmStageDefinition::successBehavior),
            Identifier.CODEC.optionalFieldOf("on_fail_behavior").forGetter(RealmStageDefinition::failBehavior)
    ).apply(instance, RealmStageDefinition::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR,
            new Reference("on_success_behavior", value.successBehavior),
            new Reference("on_fail_behavior", value.failBehavior)));
}
