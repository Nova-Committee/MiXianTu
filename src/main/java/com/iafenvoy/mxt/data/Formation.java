package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A multiblock formation's static shape, resource costs, and lifecycle actions.
 */
public record Formation(Identifier structureTemplate, NumberProvider radius,
                        Map<Holder<Resource>, NumberProvider> maxBonus, List<ResourceCost> activationCosts,
                        List<ResourceCost> maintenanceCosts, BlockAction activateAction,
                        BlockAction tickAction, BlockAction deactivateAction,
                        EntityAction entityTickAction,
                        Optional<Holder<AuraZone>> auraZone) {
    public static final Codec<Holder<Formation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORMATION);
    public static final Codec<Formation> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("structure_template").forGetter(Formation::structureTemplate),
            NumberProvider.CODEC.fieldOf("radius").forGetter(Formation::radius),
            CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC).optionalFieldOf("max_bonus", Map.of()).forGetter(Formation::maxBonus),
            ResourceCost.LIST_CODEC.optionalFieldOf("activation_costs", List.of()).forGetter(Formation::activationCosts),
            ResourceCost.LIST_CODEC.optionalFieldOf("maintenance_costs", List.of()).forGetter(Formation::maintenanceCosts),
            BlockAction.optionalCodec("activate_action").forGetter(Formation::activateAction),
            BlockAction.optionalCodec("tick_action").forGetter(Formation::tickAction),
            BlockAction.optionalCodec("deactivate_action").forGetter(Formation::deactivateAction),
            EntityAction.optionalCodec("entity_tick_action").forGetter(Formation::entityTickAction),
            AuraZone.CODEC.optionalFieldOf("aura_zone").forGetter(Formation::auraZone)
    ).apply(i, Formation::new));
}
