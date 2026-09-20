package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * A spirit root always binds exactly one element.
 *
 * <p>{@code conflicting_elements} is the one rule a root can state about its neighbours: a root whose element
 * carries one of these - or whose element is carried by one of them - cannot be held at the same time. It is a
 * list of its own rather than a reading of the element relations, because two elements may be opposed in the
 * damage pipeline and still be perfectly possible to hold together; a pack that wants "fire and water do not
 * mix in one body" says so here. The check is symmetric, so writing the rule on either root is enough.</p>
 */
public record SpiritRoot(Holder<Element> element, NumberProvider cultivationMultiplier,
                         NumberProvider elementAbilityModifier, String rarity,
                         List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                         List<Either<Holder<Element>, TagKey<Element>>> conflictingElements) {
    public static final Codec<Holder<SpiritRoot>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SPIRIT_ROOT);
    public static final Codec<SpiritRoot> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Element.CODEC.fieldOf("element").forGetter(SpiritRoot::element),
            NumberProvider.CODEC.optionalFieldOf("cultivation_multiplier", new Constant(1.0D)).forGetter(SpiritRoot::cultivationMultiplier),
            NumberProvider.CODEC.optionalFieldOf("element_ability_modifier", new Constant(1.0D)).forGetter(SpiritRoot::elementAbilityModifier),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(SpiritRoot::rarity),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(SpiritRoot::grantedAbilities),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("conflicting_elements", List.of()).forGetter(SpiritRoot::conflictingElements)
    ).apply(i, SpiritRoot::new));

    /**
     * Whether the two roots rule each other out, read in both directions so a pack writes the rule once.
     */
    public boolean conflictsWith(SpiritRoot other) {
        return RegistryCodecs.matches(this.conflictingElements, other.element())
                || RegistryCodecs.matches(other.conflictingElements(), this.element());
    }
}
