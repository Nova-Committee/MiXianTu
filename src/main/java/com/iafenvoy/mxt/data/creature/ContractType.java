package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.bientity.meta.BiEntityNoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Contract constraints and server-side lifecycle actions.
 */
public record ContractType(EntityCondition ownerCondition, EntityCondition creatureCondition,
                           EntityAction followAction, BiEntityAction combatAction,
                           EntityAction breakAction, EntityAction penaltyAction) {
    public static final Codec<ContractType> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityCondition.CODEC.optionalFieldOf("owner_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(ContractType::ownerCondition),
            EntityCondition.CODEC.optionalFieldOf("creature_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(ContractType::creatureCondition),
            EntityAction.CODEC.optionalFieldOf("follow_action", NoOpAction.INSTANCE).forGetter(ContractType::followAction),
            BiEntityAction.CODEC.optionalFieldOf("combat_action", BiEntityNoOpAction.INSTANCE).forGetter(ContractType::combatAction),
            EntityAction.CODEC.optionalFieldOf("break_action", NoOpAction.INSTANCE).forGetter(ContractType::breakAction),
            EntityAction.CODEC.optionalFieldOf("penalty_action", NoOpAction.INSTANCE).forGetter(ContractType::penaltyAction)
    ).apply(i, ContractType::new));
}
