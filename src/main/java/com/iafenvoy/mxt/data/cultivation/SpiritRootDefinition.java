package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A spirit root always binds exactly one element.
 */
public record SpiritRootDefinition(Identifier element,
                                   NumberProvider cultivationMultiplier,
                                   NumberProvider elementAbilityModifier, String rarity,
                                   List<Identifier> grantedAbilities,
                                   List<Identifier> compatibilityTags) {
    public static final Codec<SpiritRootDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("element").forGetter(SpiritRootDefinition::element),
            NumberProvider.CODEC.optionalFieldOf("cultivation_multiplier", new Constant(1.0D)).forGetter(SpiritRootDefinition::cultivationMultiplier),
            NumberProvider.CODEC.optionalFieldOf("element_ability_modifier", new Constant(1.0D)).forGetter(SpiritRootDefinition::elementAbilityModifier),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(SpiritRootDefinition::rarity),
            Identifier.CODEC.listOf().optionalFieldOf("granted_abilities", List.of()).forGetter(SpiritRootDefinition::grantedAbilities),
            Identifier.CODEC.listOf().optionalFieldOf("compatibility_tags", List.of()).forGetter(SpiritRootDefinition::compatibilityTags)
    ).apply(instance, SpiritRootDefinition::new));
}
