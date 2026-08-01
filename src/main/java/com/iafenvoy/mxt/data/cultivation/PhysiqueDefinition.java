package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.util.codec.ForbiddenFieldsCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Element-independent innate or acquired physique. Intentionally has no element field.
 */
public record PhysiqueDefinition(
                                 List<AttributeModifierDefinition> attributeModifiers,
                                 List<Identifier> grantedAbilities, List<Identifier> holderConditions,
                                 List<Identifier> exclusiveTags, String rarity, boolean allowStacking) {
    private static final Codec<PhysiqueDefinition> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("attribute_modifiers", List.of()).forGetter(PhysiqueDefinition::attributeModifiers),
            Identifier.CODEC.listOf().optionalFieldOf("granted_abilities", List.of()).forGetter(PhysiqueDefinition::grantedAbilities),
            Identifier.CODEC.listOf().optionalFieldOf("holder_conditions", List.of()).forGetter(PhysiqueDefinition::holderConditions),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(PhysiqueDefinition::exclusiveTags),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(PhysiqueDefinition::rarity),
            Codec.BOOL.optionalFieldOf("allow_stacking", false).forGetter(PhysiqueDefinition::allowStacking)
    ).apply(instance, PhysiqueDefinition::new));

    public static final Codec<PhysiqueDefinition> CODEC = ForbiddenFieldsCodec.reject(BASE_CODEC, "element", "element_tags", "relations");
}
