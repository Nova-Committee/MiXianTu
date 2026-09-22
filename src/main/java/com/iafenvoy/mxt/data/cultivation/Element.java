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
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An element definition: relation edges carrying their own damage multipliers, the damage types it claims, its
 * buildup/decay numbers, display color and conflict multiplier. {@code overcomes} is read on the attacking side
 * and {@code adapted_to} on the defending one; every matching relation multiplies, so a value below 1 weakens.
 * All numbers are validated finite and non-negative at load.
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

    // Duplicate targets are legal - every matching relation multiplies - but far more often a copy-paste slip
    // whose symptom (damage scaled by the square of one number) is invisible in play, so the load logs it once.
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

    public boolean claims(Holder<DamageType> type) {
        return this.damageTypes.stream().anyMatch(claim -> claim.matches(type));
    }

    // Matches whatever the relation is worth, so a harmless 1.0 edge still counts as a pairing.
    public boolean overcomes(Holder<Element> other) {
        return this.overcomes.stream().anyMatch(relation -> relation.matches(other));
    }

    public boolean adapts(Holder<Element> other) {
        return this.adaptedTo.stream().anyMatch(relation -> relation.matches(other));
    }

    public double overcomeMultiplier(Holder<Element> other) {
        return product(this.overcomes, other);
    }

    public double adaptationMultiplier(Holder<Element> other) {
        return product(this.adaptedTo, other);
    }

    private static double product(List<Relation> relations, Holder<Element> other) {
        double result = 1.0D;
        for (Relation relation : relations)
            if (relation.matches(other)) result *= relation.multiplier();
        return result;
    }

    // Do not expand element holders here: relations can form cycles, and a holder's diagnostic string delegates
    // back to its value's toString().
    @Override
    public @NonNull String toString() {
        return "Element[overcomes=" + this.overcomes.size()
                + ", adaptedTo=" + this.adaptedTo.size()
                + ", damageTypes=" + this.damageTypes.size() + "]";
    }
    /** One relation edge: the elements it points at (ids, tags, or both) and what the edge is worth. */
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
