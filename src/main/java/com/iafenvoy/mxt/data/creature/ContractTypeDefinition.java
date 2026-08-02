package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Contract constraints and server-side lifecycle behaviour selectors.
 */
public record ContractTypeDefinition(List<CultivationCondition> ownerConditions,
                                     List<CultivationCondition> creatureConditions, Optional<Identifier> followBehavior,
                                     Optional<Identifier> combatBehavior, Optional<Identifier> breakBehavior,
                                     Optional<Identifier> penaltyBehavior) {
    public static final Codec<ContractTypeDefinition> CODEC = RecordCodecBuilder.<ContractTypeDefinition>create(instance -> instance.group(
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("owner_conditions", List.of()).forGetter(ContractTypeDefinition::ownerConditions), AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("creature_conditions", List.of()).forGetter(ContractTypeDefinition::creatureConditions),
            Identifier.CODEC.optionalFieldOf("follow_behavior").forGetter(ContractTypeDefinition::followBehavior), Identifier.CODEC.optionalFieldOf("combat_behavior").forGetter(ContractTypeDefinition::combatBehavior),
            Identifier.CODEC.optionalFieldOf("break_behavior").forGetter(ContractTypeDefinition::breakBehavior), Identifier.CODEC.optionalFieldOf("penalty_behavior").forGetter(ContractTypeDefinition::penaltyBehavior)
    ).apply(instance, ContractTypeDefinition::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.CONTRACT_LIFECYCLE_BEHAVIOR,
            new Reference("follow_behavior", value.followBehavior),
            new Reference("combat_behavior", value.combatBehavior),
            new Reference("break_behavior", value.breakBehavior),
            new Reference("penalty_behavior", value.penaltyBehavior)));
}
