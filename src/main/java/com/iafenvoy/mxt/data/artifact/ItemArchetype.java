package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * Static rules shared by items tagged or configured as a cultivation artifact.
 */
public record ItemArchetype(String itemType, NumberProvider spiritCapacity, NumberProvider storageSlots,
                            NumberProvider flightSpeed, List<ResourceCost> flightCosts,
                            List<Holder<Ability>> grantedAbilities, Optional<Identifier> refineBehavior) {
    public static final Codec<ItemArchetype> CODEC = RecordCodecBuilder.<ItemArchetype>create(instance -> instance.group(
            Codec.STRING.fieldOf("item_type").forGetter(ItemArchetype::itemType),
            NumberProvider.CODEC.optionalFieldOf("spirit_capacity", new Constant(0.0D)).forGetter(ItemArchetype::spiritCapacity),
            NumberProvider.CODEC.optionalFieldOf("storage_slots", new Constant(0.0D)).forGetter(ItemArchetype::storageSlots),
            NumberProvider.CODEC.optionalFieldOf("flight_speed", new Constant(0.0D)).forGetter(ItemArchetype::flightSpeed),
            ResourceCost.LIST_CODEC.optionalFieldOf("flight_costs", List.of()).forGetter(ItemArchetype::flightCosts),
            AutoIgnoreListCodec.create(Ability.CODEC).optionalFieldOf("granted_abilities", List.of()).forGetter(ItemArchetype::grantedAbilities),
            Identifier.CODEC.optionalFieldOf("refine_behavior").forGetter(ItemArchetype::refineBehavior)
    ).apply(instance, ItemArchetype::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.ARTIFACT_LIFECYCLE_BEHAVIOR,
            new Reference("refine_behavior", value.refineBehavior)));
}
