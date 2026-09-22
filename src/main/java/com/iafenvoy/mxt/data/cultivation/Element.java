package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Element relations, the strength of each relation, the damage types it speaks for, and the display color used
 * by aura-cost text are data driven; tags classify elements but do not encode precedence.
 *
 * <p>Both relations carry their own damage multiplier, because "who overcomes whom" is not the whole of the
 * rule: fire overcoming metal and fire overcoming wood are different amounts, and a pack that wants a
 * three-way cycle with one soft edge writes it here rather than in code. {@code overcomes} is read on the
 * attacking side of one strike ({@code overcomes[].multiplier} multiplies the damage the holder deals to a
 * target carrying a listed element), {@code adapted_to} on the defending side ({@code adapted_to[].multiplier}
 * multiplies the damage the holder takes from a listed element). A relation below {@code 1} therefore weakens
 * rather than strengthens, which is also how a pack expresses "this element is soft against that one".</p>
 *
 * <p>Several relations can match one strike, and each one multiplies: a holder with two roots that both
 * overcome the target takes both edges, and the numbers a pack writes are the whole of the result. Every
 * multiplier must be finite and non-negative, refused at load rather than clamped at runtime.</p>
 *
 * <p>{@code damage_types} is what makes a strike elemental at all: a damage type an element claims <em>means</em>
 * that element, so a fireball, a lava bath and a blade of fire are all readable as one thing from the
 * {@link net.minecraft.world.damagesource.DamageSource} alone. An element that claims nothing is still a valid
 * element - it simply gives no damage type a meaning, and strikes keep being read off the attacker's spirit
 * roots as they were before. See {@code DamageElements} for the reading side.</p>
 *
 * <p>{@code attachment_decay} and {@code damage_attachment} describe how the element builds up on a body
 * ({@code ElementReactionService}): the first is how much of it leaves per tick on its own, the second how much
 * a strike made of it leaves behind on the target. Both default to zero, so an element is by default a pure
 * relation and a pack opts into accumulation by writing one or both numbers. Only a strike the damage type
 * <em>claims</em> leaves anything behind: a strike read off the attacker's spirit roots reduces but does not
 * rub off, which is what the origin in {@code DamageElements} records.</p>
 *
 * <p>{@code damage_attachment} is the default for every claim this element writes; a {@link DamageTypeClaim}
 * may carry a number of its own, which is how one element says that a lava bath builds up half as fast as a
 * fireball. The claimed types therefore double as groups that can each carry their own number, without the
 * element having to be split in two.</p>
 *
 * <p>{@code conflict_multiplier} is what this element is worth in the hand of somebody it conflicts with: when
 * a striker's active spirit root lists this element in its {@code conflicting_elements}, everything that
 * striker deals is multiplied by this number (default {@code 1.0}, so nothing happens unless a pack says so).
 * The number lives on the element being wielded rather than on the root, because it is that element's own
 * statement about being mis-wielded; it is applied once per wielding element no matter how many roots
 * conflict with it.</p>
 */
public record Element(List<Relation> overcomes, List<Relation> adaptedTo,
                      List<DamageTypeClaim> damageTypes,
                      double attachmentDecay, double damageAttachment, int color, double conflictMultiplier) {
    public static final Codec<Holder<Element>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ELEMENT);
    public static final Codec<Element> DIRECT_CODEC = RecordCodecBuilder.<Element>create(i -> i.group(
            Relation.CODEC.listOf().optionalFieldOf("overcomes", List.of()).forGetter(Element::overcomes),
            Relation.CODEC.listOf().optionalFieldOf("adapted_to", List.of()).forGetter(Element::adaptedTo),
            DamageTypeClaim.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(Element::damageTypes),
            Codec.DOUBLE.optionalFieldOf("attachment_decay", 0.0D).forGetter(Element::attachmentDecay),
            Codec.DOUBLE.optionalFieldOf("damage_attachment", 0.0D).forGetter(Element::damageAttachment),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("color", 0xFFFFFF).forGetter(Element::color),
            Codec.DOUBLE.optionalFieldOf("conflict_multiplier", 1.0D).forGetter(Element::conflictMultiplier)
    ).apply(i, Element::new)).validate(Element::validate);

    private static DataResult<Element> validate(Element element) {
        String overcome = invalid("overcomes", element.overcomes);
        String failure = overcome != null ? overcome : invalid("adapted_to", element.adaptedTo);
        if (failure == null && (!finite(element.attachmentDecay) || !finite(element.damageAttachment)))
            failure = "Element attachment numbers must be finite and non-negative";
        if (failure == null && !finite(element.conflictMultiplier))
            failure = "Element conflict_multiplier must be finite and non-negative: " + element.conflictMultiplier;
        if (failure != null) {
            String message = failure;
            return DataResult.error(() -> message);
        }
        warnRepeatedTargets("overcomes", element.overcomes);
        warnRepeatedTargets("adapted_to", element.adaptedTo);
        return DataResult.success(element);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && value >= 0.0D;
    }

    private static String invalid(String field, List<Relation> relations) {
        for (Relation relation : relations) {
            if (relation.elements().isEmpty())
                return "Element " + field + " relation must name at least one element";
            if (!Double.isFinite(relation.multiplier()) || relation.multiplier() < 0.0D)
                return "Element " + field + " multiplier must be finite and non-negative: " + relation.multiplier();
        }
        return null;
    }

    /**
     * Two relations naming the same target are legal - every matching relation multiplies, which is how a pack
     * writes "twice as strong against one element" - but they are much more often a copy-paste slip, and the
     * symptom (damage scaled by the square of one number) is invisible in play. The relation is kept and the
     * load says so once.
     */
    private static void warnRepeatedTargets(String field, List<Relation> relations) {
        Set<Holder<Element>> holders = new HashSet<>();
        Set<TagKey<Element>> tags = new HashSet<>();
        for (Relation relation : relations)
            for (Either<Holder<Element>, TagKey<Element>> target : relation.elements()) {
                boolean repeated = target.left().map(holder -> !holders.add(holder))
                        .orElseGet(() -> !tags.add(target.right().orElseThrow()));
                if (repeated) {
                    MiXianTu.LOGGER.warn("An element lists the same {} target in more than one relation; every matching relation multiplies, so such a strike is scaled by each of them", field);
                    return;
                }
            }
    }

    /**
     * Whether this element claims the given damage type, tags included. The claim is what names the element of
     * a strike; the relation multipliers are what that element is then worth, and the claim's own
     * {@code damage_attachment} is how much of it that kind of hit leaves behind.
     */
    public boolean claims(Holder<DamageType> type) {
        return this.damageTypes.stream().anyMatch(claim -> claim.matches(type));
    }

    /**
     * Whether this element overcomes the given one at all, whatever the relation is worth. A condition that
     * asks about the relation itself reads this, so a pack can write a harmless {@code 1.0} edge for content
     * that only needs the pairing.
     */
    public boolean overcomes(Holder<Element> other) {
        return this.overcomes.stream().anyMatch(relation -> relation.matches(other));
    }

    /**
     * Whether this element is adapted to the given one at all, whatever the relation is worth. The defensive
     * mirror of {@link #overcomes}: a pack reads it to ask "is this something I resist", and cultivation reads
     * it to decide whether a place is opposed to a root rather than merely empty of it.
     */
    public boolean adapts(Holder<Element> other) {
        return this.adaptedTo.stream().anyMatch(relation -> relation.matches(other));
    }

    /**
     * The damage multiplier this element deals to a target whose element is the given one.
     */
    public double overcomeMultiplier(Holder<Element> other) {
        return product(this.overcomes, other);
    }

    /**
     * The damage multiplier this element takes from an attack whose element is the given one.
     */
    public double adaptationMultiplier(Holder<Element> other) {
        return product(this.adaptedTo, other);
    }

    private static double product(List<Relation> relations, Holder<Element> other) {
        double result = 1.0D;
        for (Relation relation : relations)
            if (relation.matches(other)) result *= relation.multiplier();
        return result;
    }

    /**
     * Do not expand element holders here. Element relations can form cycles, and a holder's
     * diagnostic string delegates back to its value's {@code toString()}.
     */
    @Override
    public @NonNull String toString() {
        return "Element[overcomes=" + this.overcomes.size()
                + ", adaptedTo=" + this.adaptedTo.size()
                + ", damageTypes=" + this.damageTypes.size() + "]";
    }
    /**
     * One edge of a relation: the elements it points at, written as registry entries, tags, or both, and what
     * the edge is worth. The list shape is the same holder-or-tag list the rest of the mod uses, so a single id
     * and an array of ids are both accepted.
     */
    public record Relation(List<Either<Holder<Element>, TagKey<Element>>> elements, double multiplier) {
        public static final Codec<Relation> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(Relation::elements),
                Codec.DOUBLE.fieldOf("multiplier").forGetter(Relation::multiplier)
        ).apply(i, Relation::new));

        public boolean matches(Holder<Element> other) {
            return RegistryCodecs.matches(this.elements, other);
        }
    }
}
