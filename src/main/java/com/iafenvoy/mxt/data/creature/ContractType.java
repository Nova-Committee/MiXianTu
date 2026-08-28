package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Contract constraints and server-side lifecycle actions.
 */
public record ContractType(EntityCondition ownerCondition, EntityCondition creatureCondition,
                           EntityAction followAction, BiEntityAction combatAction,
                           EntityAction breakAction, EntityAction penaltyAction) {
    public static final Codec<ContractType> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityCondition.optionalCodec("owner_condition").forGetter(ContractType::ownerCondition),
            EntityCondition.optionalCodec("creature_condition").forGetter(ContractType::creatureCondition),
            EntityAction.optionalCodec("follow_action").forGetter(ContractType::followAction),
            BiEntityAction.optionalCodec("combat_action").forGetter(ContractType::combatAction),
            EntityAction.optionalCodec("break_action").forGetter(ContractType::breakAction),
            EntityAction.optionalCodec("penalty_action").forGetter(ContractType::penaltyAction)
    ).apply(i, ContractType::new));
}
