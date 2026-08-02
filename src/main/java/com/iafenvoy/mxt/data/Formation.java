package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Optional;

/**
 * A multiblock formation's static shape, resource costs, and lifecycle behaviour IDs.
 */
public record Formation(Identifier structureTemplate, NumberProvider radius, List<ResourceCost> activationCosts,
                        List<ResourceCost> maintenanceCosts, Optional<Identifier> activateBehavior,
                        Optional<Identifier> maintainBehavior, Optional<Identifier> triggerBehavior,
                        Optional<Holder<AuraZone>> auraZone) {
    public static final Codec<Formation> DIRECT_CODEC = RecordCodecBuilder.<Formation>create(instance -> instance.group(
            Identifier.CODEC.fieldOf("structure_template").forGetter(Formation::structureTemplate),
            NumberProvider.CODEC.fieldOf("radius").forGetter(Formation::radius),
            ResourceCost.LIST_CODEC.optionalFieldOf("activation_costs", List.of()).forGetter(Formation::activationCosts),
            ResourceCost.LIST_CODEC.optionalFieldOf("maintenance_costs", List.of()).forGetter(Formation::maintenanceCosts),
            Identifier.CODEC.optionalFieldOf("activate_behavior").forGetter(Formation::activateBehavior),
            Identifier.CODEC.optionalFieldOf("maintain_behavior").forGetter(Formation::maintainBehavior),
            Identifier.CODEC.optionalFieldOf("trigger_behavior").forGetter(Formation::triggerBehavior),
            AuraZone.CODEC.optionalFieldOf("aura_zone").forGetter(Formation::auraZone)
    ).apply(instance, Formation::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.FORMATION_LIFECYCLE_BEHAVIOR,
            new Reference("activate_behavior", value.activateBehavior),
            new Reference("maintain_behavior", value.maintainBehavior),
            new Reference("trigger_behavior", value.triggerBehavior)));
    public static final Codec<Holder<Formation>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.FORMATION);
}
