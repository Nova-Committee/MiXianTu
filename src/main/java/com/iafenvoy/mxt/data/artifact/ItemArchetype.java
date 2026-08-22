package com.iafenvoy.mxt.data.artifact;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.meta.NoOpItemAction;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * Static rules shared by items tagged or configured as a cultivation artifact.
 */
public record ItemArchetype(String itemType, NumberProvider spiritCapacity, NumberProvider storageSlots,
                            NumberProvider flightSpeed, List<ResourceCost> flightCosts,
                            List<Holder<Ability>> grantedAbilities, ItemAction refineAction) {
    public static final Codec<Holder<ItemArchetype>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ITEM_ARCHETYPE);
    public static final Codec<ItemArchetype> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item_type").forGetter(ItemArchetype::itemType),
            NumberProvider.CODEC.optionalFieldOf("spirit_capacity", new Constant(0.0D)).forGetter(ItemArchetype::spiritCapacity),
            NumberProvider.CODEC.optionalFieldOf("storage_slots", new Constant(0.0D)).forGetter(ItemArchetype::storageSlots),
            NumberProvider.CODEC.optionalFieldOf("flight_speed", new Constant(0.0D)).forGetter(ItemArchetype::flightSpeed),
            ResourceCost.LIST_CODEC.optionalFieldOf("flight_costs", List.of()).forGetter(ItemArchetype::flightCosts),
            AutoIgnoreListCodec.create(Ability.CODEC).optionalFieldOf("granted_abilities", List.of()).forGetter(ItemArchetype::grantedAbilities),
            ItemAction.CODEC.optionalFieldOf("refine_action", NoOpItemAction.INSTANCE).forGetter(ItemArchetype::refineAction)
    ).apply(i, ItemArchetype::new));
}
