package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.data.ability.Ability;
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
public record SpiritRoot(Holder<Element> element, NumberProvider cultivationMultiplier,
                         NumberProvider elementAbilityModifier, String rarity,
                         List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                         List<Identifier> compatibilityTags) {
    public static final Codec<SpiritRoot> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Element.CODEC.fieldOf("element").forGetter(SpiritRoot::element),
            NumberProvider.CODEC.optionalFieldOf("cultivation_multiplier", new Constant(1.0D)).forGetter(SpiritRoot::cultivationMultiplier),
            NumberProvider.CODEC.optionalFieldOf("element_ability_modifier", new Constant(1.0D)).forGetter(SpiritRoot::elementAbilityModifier),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(SpiritRoot::rarity),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(SpiritRoot::grantedAbilities),
            Identifier.CODEC.listOf().optionalFieldOf("compatibility_tags", List.of()).forGetter(SpiritRoot::compatibilityTags)
    ).apply(instance, SpiritRoot::new));
    public static final Codec<Holder<SpiritRoot>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.SPIRIT_ROOT);
}
