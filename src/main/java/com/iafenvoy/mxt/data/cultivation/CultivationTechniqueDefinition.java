package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A learnable technique grants named abilities and cultivation modifiers.
 */
public record CultivationTechniqueDefinition(String grade,
                                             List<Identifier> learnConditions,
                                             List<Identifier> exclusiveTags, NumberProvider cultivationModifier,
                                             List<AttributeModifierDefinition> passiveModifiers,
                                             List<Identifier> grantedAbilities) {
    public static final Codec<CultivationTechniqueDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechniqueDefinition::grade), Identifier.CODEC.listOf().optionalFieldOf("learn_conditions", List.of()).forGetter(CultivationTechniqueDefinition::learnConditions),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechniqueDefinition::exclusiveTags), NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechniqueDefinition::cultivationModifier),
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechniqueDefinition::passiveModifiers), Identifier.CODEC.listOf().optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechniqueDefinition::grantedAbilities)
    ).apply(instance, CultivationTechniqueDefinition::new));
}
