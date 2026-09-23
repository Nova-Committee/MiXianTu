package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * A spirit root always binds exactly one element. {@code conflicting_elements} is a list of its own rather than a
 * reading of the element relations - two elements may be opposed in the damage pipeline and still be perfectly
 * possible to hold together - and the check is symmetric, so writing the rule on either root is enough.
 */
public record SpiritRoot(Component name, Component description, Holder<Element> element,
                         NumberProvider cultivationMultiplier,
                         NumberProvider elementAbilityModifier, String rarity,
                         List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                         List<Either<Holder<Element>, TagKey<Element>>> conflictingElements) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.SPIRIT_ROOT.identifier());
    public static final Codec<Holder<SpiritRoot>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SPIRIT_ROOT);
    public static final Codec<SpiritRoot> DIRECT_CODEC = RecordCodecBuilder.<SpiritRoot>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(SpiritRoot::name),
            ContextNameCodec.description(CATEGORY).forGetter(SpiritRoot::description),
            Element.CODEC.fieldOf("element").forGetter(SpiritRoot::element),
            NumberProvider.CODEC.optionalFieldOf("cultivation_multiplier", new Constant(1.0D)).forGetter(SpiritRoot::cultivationMultiplier),
            NumberProvider.CODEC.optionalFieldOf("element_ability_modifier", new Constant(1.0D)).forGetter(SpiritRoot::elementAbilityModifier),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(SpiritRoot::rarity),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(SpiritRoot::grantedAbilities),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("conflicting_elements", List.of()).forGetter(SpiritRoot::conflictingElements)
    ).apply(i, SpiritRoot::new)).validate(SpiritRoot::validate);

    // A written multiplier is rejected at load rather than read as NaN at runtime, since -0.5 is a typo far more
    // often than a rule. A formula can only be judged when it runs, which the callers already do.
    private static DataResult<SpiritRoot> validate(SpiritRoot root) {
        for (NumberProvider provider : List.of(root.cultivationMultiplier(), root.elementAbilityModifier()))
            if (provider instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
                return DataResult.error(() -> "A spirit root multiplier must be finite and non-negative: " + value);
        return DataResult.success(root);
    }

    // Both elements must be live for the question to mean anything: a mxt:disabled element neither rules another
    // out nor is ruled out by one, so a root bound to it coexists with everything.
    public boolean conflictsWith(SpiritRoot other) {
        if (!enabled(this.element) || !enabled(other.element())) return false;
        return RegistryCodecs.matches(this.conflictingElements, other.element())
                || RegistryCodecs.matches(other.conflictingElements(), this.element());
    }

    private static boolean enabled(Holder<Element> element) {
        return !MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element);
    }
}
