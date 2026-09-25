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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.function.Function;

/**
 * A spirit root binds one or more elements; holding several is how the content side says "mixed root", and a
 * weight says how much of the root each element is (see {@link ElementWeight}). Every reader takes the set as a
 * whole: membership questions ignore the weights, the cultivation affinity and the element opposition answers
 * are weighted (see {@code CultivationAffinity}). {@code conflicting_elements} is a list of its own rather than
 * a reading of the element relations - two elements may be opposed in the damage pipeline and still be
 * perfectly possible to hold together - and the check is symmetric, so writing the rule on either root is enough.
 */
public record SpiritRoot(Component name, Component description, List<ElementWeight> elements,
                         NumberProvider cultivationMultiplier,
                         NumberProvider elementAbilityModifier, String rarity,
                         List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                         List<Either<Holder<Element>, TagKey<Element>>> conflictingElements) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.SPIRIT_ROOT.identifier());
    public static final Codec<Holder<SpiritRoot>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SPIRIT_ROOT);
    public static final Codec<SpiritRoot> DIRECT_CODEC = RecordCodecBuilder.<SpiritRoot>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(SpiritRoot::name),
            ContextNameCodec.description(CATEGORY).forGetter(SpiritRoot::description),
            ElementWeight.CODEC.listOf().fieldOf("elements").forGetter(SpiritRoot::elements),
            NumberProvider.CODEC.optionalFieldOf("cultivation_multiplier", new Constant(1.0D)).forGetter(SpiritRoot::cultivationMultiplier),
            NumberProvider.CODEC.optionalFieldOf("element_ability_modifier", new Constant(1.0D)).forGetter(SpiritRoot::elementAbilityModifier),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(SpiritRoot::rarity),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(SpiritRoot::grantedAbilities),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("conflicting_elements", List.of()).forGetter(SpiritRoot::conflictingElements)
    ).apply(i, SpiritRoot::new)).validate(SpiritRoot::validate);

    /**
     * One element of a root and its share of it. Weights are proportions rather than multipliers: they are
     * normalised where they are read, so {@code [1, 1]} and {@code [0.5, 0.5]} mean the same thing, and a root
     * that binds one element is unaffected by the number written on it.
     */
    public record ElementWeight(Holder<Element> element, double weight) {
        private static final MapCodec<ElementWeight> RAW_CODEC = RecordCodecBuilder.<ElementWeight>mapCodec(i -> i.group(
                Element.CODEC.fieldOf("element").forGetter(ElementWeight::element),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0D).forGetter(ElementWeight::weight)
        ).apply(i, ElementWeight::new));

        // A bare entry id is the whole share of one element, which is what every single-element root writes; the
        // object form adds a weight. A weight of exactly one is written back in the short form.
        public static final Codec<ElementWeight> CODEC = Codec.either(Element.CODEC, RAW_CODEC.codec())
                .xmap(ElementWeight::fromEither, ElementWeight::toEither).validate(ElementWeight::validate);

        private static ElementWeight fromEither(Either<Holder<Element>, ElementWeight> value) {
            return value.map(ElementWeight::withoutWeight, Function.identity());
        }

        private static Either<Holder<Element>, ElementWeight> toEither(ElementWeight value) {
            return value.weight() == 1.0D ? Either.left(value.element()) : Either.right(value);
        }

        private static DataResult<ElementWeight> validate(ElementWeight value) {
            return Double.isFinite(value.weight) && value.weight > 0.0D ? DataResult.success(value)
                    : DataResult.error(() -> "An element weight must be finite and positive: " + value.weight);
        }

        public static ElementWeight withoutWeight(Holder<Element> element) {
            return new ElementWeight(element, 1.0D);
        }
    }

    // A written multiplier is rejected at load rather than read as NaN at runtime, since -0.5 is a typo far more
    // often than a rule. A formula can only be judged when it runs, which the callers already do.
    private static DataResult<SpiritRoot> validate(SpiritRoot root) {
        for (NumberProvider provider : List.of(root.cultivationMultiplier(), root.elementAbilityModifier()))
            if (provider instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
                return DataResult.error(() -> "A spirit root multiplier must be finite and non-negative: " + value);
        // No element means no root at all, and a repeated element is a typo rather than a share written twice.
        if (root.elements().isEmpty())
            return DataResult.error(() -> "A spirit root must name at least one element: elements is empty");
        if (root.elementHolders().stream().distinct().count() != root.elements().size())
            return DataResult.error(() -> "A spirit root must not name the same element twice: " + root.elementHolders());
        double total = root.totalWeight();
        if (!Double.isFinite(total) || total <= 0.0D)
            return DataResult.error(() -> "The element weights of a spirit root must add up to a positive, finite number: " + total);
        return DataResult.success(root);
    }

    // What the membership questions read: whether a body carries an element never depends on its share.
    public List<Holder<Element>> elementHolders() {
        return this.elements.stream().map(ElementWeight::element).toList();
    }

    // The denominator every weighted reading divides by, so a root's weights are only ever proportions.
    public double totalWeight() {
        return this.elements.stream().mapToDouble(ElementWeight::weight).sum();
    }

    // Two roots conflict when either one names an element the other holds. Both directions are asked because a
    // pack writes the rule on whichever root it thinks of first.
    public boolean conflictsWith(SpiritRoot other) {
        return this.elementHolders().stream().anyMatch(element -> names(other.conflictingElements(), element))
                || other.elementHolders().stream().anyMatch(element -> names(this.conflictingElements, element));
    }

    // Both elements must be live for the question to mean anything: a mxt:disabled element neither rules another
    // out nor is ruled out by one, so a root bound to it coexists with everything.
    private static boolean names(List<Either<Holder<Element>, TagKey<Element>>> declared, Holder<Element> element) {
        return enabled(element) && RegistryCodecs.matches(declared, element);
    }

    private static boolean enabled(Holder<Element> element) {
        return !MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element);
    }
}
