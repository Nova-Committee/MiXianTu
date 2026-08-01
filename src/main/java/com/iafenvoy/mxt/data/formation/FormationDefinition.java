package com.iafenvoy.mxt.data.formation;

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
 * A multiblock formation's static shape, resource costs, and lifecycle behaviour IDs.
 */
public record FormationDefinition(Identifier structureTemplate,
                                  NumberProvider radius,
                                  List<ResourceCost> activationCosts, List<ResourceCost> maintenanceCosts,
                                  Optional<Identifier> activateBehavior, Optional<Identifier> maintainBehavior,
                                  Optional<Identifier> triggerBehavior) {
    public static final Codec<FormationDefinition> CODEC = RecordCodecBuilder.<FormationDefinition>create(instance -> instance.group(
            Identifier.CODEC.fieldOf("structure_template").forGetter(FormationDefinition::structureTemplate), NumberProvider.CODEC.fieldOf("radius").forGetter(FormationDefinition::radius),
            ResourceCost.CODEC.listOf().optionalFieldOf("activation_costs", List.of()).forGetter(FormationDefinition::activationCosts), ResourceCost.CODEC.listOf().optionalFieldOf("maintenance_costs", List.of()).forGetter(FormationDefinition::maintenanceCosts),
            Identifier.CODEC.optionalFieldOf("activate_behavior").forGetter(FormationDefinition::activateBehavior), Identifier.CODEC.optionalFieldOf("maintain_behavior").forGetter(FormationDefinition::maintainBehavior), Identifier.CODEC.optionalFieldOf("trigger_behavior").forGetter(FormationDefinition::triggerBehavior)
    ).apply(instance, FormationDefinition::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.FORMATION_LIFECYCLE_BEHAVIOR,
            new Reference("activate_behavior", value.activateBehavior),
            new Reference("maintain_behavior", value.maintainBehavior),
            new Reference("trigger_behavior", value.triggerBehavior)));
}
