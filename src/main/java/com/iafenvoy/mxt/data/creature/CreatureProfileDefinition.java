package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Datapack profile applied to tagged creature types; it does not create an entity type.
 */
public record CreatureProfileDefinition(Optional<Identifier> realmStage,
                                        NumberProvider intelligence,
                                        List<Identifier> spawnConditions, Optional<Identifier> innerCore,
                                        Optional<Identifier> lootTable,
                                        List<Identifier> contractTags, List<Identifier> entityTypeTags) {
    public static final Codec<CreatureProfileDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("realm_stage").forGetter(CreatureProfileDefinition::realmStage), NumberProvider.CODEC.optionalFieldOf("intelligence", new Constant(0.0D)).forGetter(CreatureProfileDefinition::intelligence),
            Identifier.CODEC.listOf().optionalFieldOf("spawn_conditions", List.of()).forGetter(CreatureProfileDefinition::spawnConditions), Identifier.CODEC.optionalFieldOf("inner_core").forGetter(CreatureProfileDefinition::innerCore), Identifier.CODEC.optionalFieldOf("loot_table").forGetter(CreatureProfileDefinition::lootTable),
            Identifier.CODEC.listOf().optionalFieldOf("contract_tags", List.of()).forGetter(CreatureProfileDefinition::contractTags), Identifier.CODEC.listOf().optionalFieldOf("entity_type_tags", List.of()).forGetter(CreatureProfileDefinition::entityTypeTags)
    ).apply(instance, CreatureProfileDefinition::new));
}
