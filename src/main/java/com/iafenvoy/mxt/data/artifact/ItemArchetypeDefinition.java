package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Static rules shared by items tagged or configured as a cultivation artifact.
 */
public record ItemArchetypeDefinition(String itemType,
                                      NumberProvider spiritCapacity,
                                      NumberProvider storageSlots, NumberProvider flightSpeed,
                                      List<ResourceCost> flightCosts, List<Identifier> grantedAbilities,
                                      Optional<Identifier> refineBehavior) {
    public ItemArchetypeDefinition(String itemType, NumberProvider spiritCapacity,
                                   NumberProvider storageSlots, NumberProvider flightSpeed, List<Identifier> grantedAbilities,
                                   Optional<Identifier> refineBehavior) {
        this(itemType, spiritCapacity, storageSlots, flightSpeed, List.of(), grantedAbilities, refineBehavior);
    }

    public static final Codec<ItemArchetypeDefinition> CODEC = RecordCodecBuilder.<ItemArchetypeDefinition>create(instance -> instance.group(
            Codec.STRING.fieldOf("item_type").forGetter(ItemArchetypeDefinition::itemType), NumberProvider.CODEC.optionalFieldOf("spirit_capacity", new Constant(0.0D)).forGetter(ItemArchetypeDefinition::spiritCapacity),
            NumberProvider.CODEC.optionalFieldOf("storage_slots", new Constant(0.0D)).forGetter(ItemArchetypeDefinition::storageSlots), NumberProvider.CODEC.optionalFieldOf("flight_speed", new Constant(0.0D)).forGetter(ItemArchetypeDefinition::flightSpeed), ResourceCost.CODEC.listOf().optionalFieldOf("flight_costs", List.of()).forGetter(ItemArchetypeDefinition::flightCosts),
            Identifier.CODEC.listOf().optionalFieldOf("granted_abilities", List.of()).forGetter(ItemArchetypeDefinition::grantedAbilities), Identifier.CODEC.optionalFieldOf("refine_behavior").forGetter(ItemArchetypeDefinition::refineBehavior)
    ).apply(instance, ItemArchetypeDefinition::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.ARTIFACT_LIFECYCLE_BEHAVIOR,
            new Reference("refine_behavior", value.refineBehavior)));
}
