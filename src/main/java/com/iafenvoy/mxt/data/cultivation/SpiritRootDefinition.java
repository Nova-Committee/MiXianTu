package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * A spirit root always binds exactly one element.
 */
public record SpiritRootDefinition(Holder<ElementDefinition> element,
                                   NumberProvider cultivationMultiplier,
                                   NumberProvider elementAbilityModifier, String rarity,
                                   List<Either<Holder<AbilityDefinition>, TagKey<AbilityDefinition>>> grantedAbilities,
                                   List<Identifier> compatibilityTags) {
    public static final Codec<Holder<SpiritRootDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.SPIRIT_ROOT);
    public static final Codec<SpiritRootDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ElementDefinition.HOLDER_CODEC.fieldOf("element").forGetter(SpiritRootDefinition::element),
            NumberProvider.CODEC.optionalFieldOf("cultivation_multiplier", new Constant(1.0D)).forGetter(SpiritRootDefinition::cultivationMultiplier),
            NumberProvider.CODEC.optionalFieldOf("element_ability_modifier", new Constant(1.0D)).forGetter(SpiritRootDefinition::elementAbilityModifier),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(SpiritRootDefinition::rarity),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(SpiritRootDefinition::grantedAbilities),
            Identifier.CODEC.listOf().optionalFieldOf("compatibility_tags", List.of()).forGetter(SpiritRootDefinition::compatibilityTags)
    ).apply(instance, SpiritRootDefinition::new));
}
