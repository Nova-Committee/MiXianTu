package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

/**
 * Contract constraints and server-side lifecycle actions.
 */
public record ContractType(Component name, Component description, EntityCondition ownerCondition,
                           EntityCondition creatureCondition,
                           EntityAction followAction, BiEntityAction combatAction,
                           EntityAction breakAction, EntityAction penaltyAction) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.CONTRACT_TYPE.identifier());
    public static final Codec<ContractType> CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(ContractType::name),
            ContextNameCodec.description(CATEGORY).forGetter(ContractType::description),
            EntityCondition.optionalCodec("owner_condition").forGetter(ContractType::ownerCondition),
            EntityCondition.optionalCodec("creature_condition").forGetter(ContractType::creatureCondition),
            EntityAction.optionalCodec("follow_action").forGetter(ContractType::followAction),
            BiEntityAction.optionalCodec("combat_action").forGetter(ContractType::combatAction),
            EntityAction.optionalCodec("break_action").forGetter(ContractType::breakAction),
            EntityAction.optionalCodec("penalty_action").forGetter(ContractType::penaltyAction)
    ).apply(i, ContractType::new));
}
