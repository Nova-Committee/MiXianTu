package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Contract constraints, price and the effects of each moment of the contract. Every action field answers one
 * moment only: a release and a death are two different events and never share a field.
 */
// TODO: may be removed. Eligibility is a code fact and the owner belongs to the entity, so what is left here is
// the two conditions, the actions, the price and the caps; ContractService, the type stored in
// ContractAttachment and the /contract command would go with it if the creature ever declares that itself.
// Marked, not scheduled.
public record ContractType(Component name, Component description, EntityCondition ownerCondition,
                           EntityCondition creatureCondition, EntityAction followAction, BiEntityAction combatAction,
                           EntityAction releaseAction, EntityAction deathAction, List<Cost> costs, int maxOwned,
                           int recallCooldown) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.CONTRACT_TYPE.identifier());
    public static final Codec<ContractType> CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(ContractType::name),
            ContextNameCodec.description(CATEGORY).forGetter(ContractType::description),
            EntityCondition.optionalCodec("owner_condition").forGetter(ContractType::ownerCondition),
            EntityCondition.optionalCodec("creature_condition").forGetter(ContractType::creatureCondition),
            EntityAction.optionalCodec("follow_action").forGetter(ContractType::followAction),
            BiEntityAction.optionalCodec("combat_action").forGetter(ContractType::combatAction),
            EntityAction.optionalCodec("release_action").forGetter(ContractType::releaseAction),
            EntityAction.optionalCodec("death_action").forGetter(ContractType::deathAction),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ContractType::costs),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_owned", 0).forGetter(ContractType::maxOwned),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("recall_cooldown", 0).forGetter(ContractType::recallCooldown)
    ).apply(i, ContractType::new));
}
