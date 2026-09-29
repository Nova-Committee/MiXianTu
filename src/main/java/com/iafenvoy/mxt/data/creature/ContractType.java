package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Contract constraints, price and the effects of each moment of the contract. Every action field answers one
 * moment only: a release and a death are two different events and never share a field.
 *
 * <p>The owner is a second subject, so the owner side has its own moments and its own grant: an action field per
 * moment, and abilities the owner holds while the contract lasts (rebuilt from the owner-side index of bound
 * beasts, so losing one of two beasts of the same type keeps them).</p>
 */
// TODO: may be removed. Eligibility is a code fact and the owner belongs to the entity, so what is left here is
// the two conditions, the actions, the price and the caps; ContractService, the type stored in
// ContractAttachment and the /contract command would go with it if the creature ever declares that itself.
// Marked, not scheduled.
public record ContractType(Component name, Component description, EntityCondition ownerCondition,
                           EntityCondition creatureCondition, EntityAction followAction, BiEntityAction combatAction,
                           EntityAction releaseAction, EntityAction deathAction, EntityAction ownerBindAction,
                           EntityAction ownerReleaseAction, EntityAction ownerDeathAction,
                           List<Either<Holder<Ability>, TagKey<Ability>>> ownerAbilities, List<Cost> costs,
                           int maxOwned, int recallCooldown) implements NamedDefinition {
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
            EntityAction.optionalCodec("owner_bind_action").forGetter(ContractType::ownerBindAction),
            EntityAction.optionalCodec("owner_release_action").forGetter(ContractType::ownerReleaseAction),
            EntityAction.optionalCodec("owner_death_action").forGetter(ContractType::ownerDeathAction),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("owner_abilities", List.of()).forGetter(ContractType::ownerAbilities),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ContractType::costs),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_owned", 0).forGetter(ContractType::maxOwned),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("recall_cooldown", 0).forGetter(ContractType::recallCooldown)
    ).apply(i, ContractType::new));
}
