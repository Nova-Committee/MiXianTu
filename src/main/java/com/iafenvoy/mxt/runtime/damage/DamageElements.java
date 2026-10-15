package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.data.cultivation.DamageTypeClaim;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The reverse index from a damage type to the elements claiming it, and the single answer both damage layers
 * read ({@code research/29}). A type nobody claims falls back to the attacker's roots, which carry no element
 * when there is no attacker.
 */
public final class DamageElements {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Holder<DamageType>, List<Claim>> INDEX_CACHE = new LinkedHashMap<>();

    public static void clear() {
        INDEX_CACHE.clear();
    }

    public static void reload(RegistryAccess access) {
        clear();
        Registry<Element> elements = access.lookupOrThrow(MxtResourceKeys.ELEMENT);
        Registry<DamageType> types = access.lookupOrThrow(Registries.DAMAGE_TYPE);
        for (Reference<Element> element : elements.listElements().toList()) claimAll(types, element);
    }

    private static void claimAll(Registry<DamageType> types, Holder<Element> element) {
        for (DamageTypeClaim claim : element.value().damageTypes()) {
            // The claim's own number when it wrote one, the element's own default otherwise.
            double attachment = claim.attachment(element.value().damageAttachment());
            claim.type().left().ifPresentOrElse(type -> claim(type, element, attachment), () -> {
                TagKey<DamageType> tag = claim.type().right().orElseThrow();
                types.listElements().filter(type -> type.is(tag)).forEach(type -> claim(type, element, attachment));
            });
        }
    }

    private static void claim(Holder<DamageType> type, Holder<Element> element, double attachment) {
        List<Claim> claimants = INDEX_CACHE.computeIfAbsent(type, _ -> new LinkedList<>());
        for (Claim existing : claimants) if (existing.element().equals(element)) return;
        // Every claim multiplies downstream, so a second claimant for one type is worth a warning.
        if (!claimants.isEmpty())
            LOGGER.warn("Damage type {} is claimed by more than one element; every claim multiplies the element relation of such a strike", HolderHelper.id(type));
        claimants.add(new Claim(element, attachment));
    }

    public static Set<Holder<Element>> of(Holder<DamageType> type) {
        return INDEX_CACHE.getOrDefault(type, List.of()).stream().map(Claim::element).collect(Collectors.toSet());
    }

    // The single rule the pipeline and the damage condition both read, so a condition can never disagree with
    // the number that was applied. The origin is dropped - use reading(...) when the two must be told apart.
    public static Set<Holder<Element>> strike(Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        return reading(type, attacker).elements();
    }

    // strike(...) with the origin and the amounts kept: the reduction step wants the elements, the buildup step
    // also wants how much of each this kind of hit leaves.
    public static Strike reading(Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        List<Claim> claimed = type.map(type1 -> INDEX_CACHE.getOrDefault(type1, List.of())).orElse(List.of());
        return claimed.isEmpty() ? roots(attacker) : of(claimed);
    }

    // What the reduction and attachment steps of the incoming event read.
    public static Strike reading(DamageSource source, HolderLookup.Provider provider) {
        List<Claim> claimed = INDEX_CACHE.getOrDefault(source.typeHolder(), List.of());
        if (!claimed.isEmpty()) return of(claimed);
        return roots(source.getEntity());
    }

    // Nobody claimed the type, so the attacker's own roots answer. The amounts are the elements' own defaults,
    // which only matter to a caller that ignores the origin - the buildup step does not.
    private static Strike roots(@Nullable Entity attacker) {
        Set<Holder<Element>> elements = attacker == null ? Set.of() : Elements.of(attacker);
        return new Strike(elements, amounts(elements), false);
    }

    // What each element would leave behind by its own declaration.
    private static Map<Holder<Element>, Double> amounts(Set<Holder<Element>> elements) {
        Map<Holder<Element>, Double> attachment = new LinkedHashMap<>();
        for (Holder<Element> element : elements) attachment.put(element, element.value().damageAttachment());
        return Map.copyOf(attachment);
    }

    // One entry per element, in index order. The first claim an element makes over a type is the one that
    // speaks, the same reading the index itself keeps.
    private static Strike of(List<Claim> claimed) {
        Set<Holder<Element>> elements = new LinkedHashSet<>();
        Map<Holder<Element>, Double> attachment = new LinkedHashMap<>();
        for (Claim claim : claimed)
            if (elements.add(claim.element())) attachment.put(claim.element(), claim.attachment());
        return new Strike(Set.copyOf(elements), Map.copyOf(attachment), true);
    }

    // What the reduction layer is handed. The causing entity is the attacker a source is credited with, so a
    // plain mob swing claiming no damage type still reads the mob's own elements.
    public static Set<Holder<Element>> strike(DamageSource source, HolderLookup.Provider provider) {
        return reading(source, provider).elements();
    }

    // The first claim the element lists that exists, which is what lets a damage action say "this strike is
    // fire" and still have a type to travel as.
    public static Optional<Holder<DamageType>> typeOf(RegistryAccess access, Holder<Element> element) {
        Registry<DamageType> types = access.lookupOrThrow(Registries.DAMAGE_TYPE);
        for (DamageTypeClaim claim : element.value().damageTypes()) {
            if (claim.type().left().isPresent()) return claim.type().left();
            TagKey<DamageType> tag = claim.type().right().orElseThrow();
            Optional<Reference<DamageType>> tagged = types.listElements().filter(type -> type.is(tag)).findFirst();
            if (tagged.isPresent()) return Optional.of(tagged.get());
        }
        return Optional.empty();
    }

    // An empty result means the strike keeps the reading it always had (the attacker's roots). Every way a
    // declaration can fail to name a type is reported here rather than failing the load.
    public static Optional<Holder<DamageType>> resolveType(RegistryAccess access, List<Either<Holder<Element>, TagKey<Element>>> elements, Optional<Holder<DamageType>> damageType) {
        if (damageType.isPresent()) {
            if (!elements.isEmpty()) checkDeclaration(elements, damageType.get());
            return damageType;
        }
        if (elements.isEmpty()) return Optional.empty();
        Optional<Holder<Element>> element = elements.stream().flatMap(entry -> entry.left().stream()).findFirst();
        if (element.isEmpty()) {
            LOGGER.warn("A tagged element cannot name the damage type of a strike; declare damage_type as well");
            return Optional.empty();
        }
        Optional<Holder<DamageType>> resolved = typeOf(access, element.get());
        if (resolved.isEmpty())
            LOGGER.warn("Element {} claims no damage type, so a strike that declares it has no element of its own", HolderHelper.id(element.get()));
        return resolved;
    }

    // Checked on first use, not at load: the value behind a declared element is not necessarily bound yet while
    // another datapack registry page is being decoded (registries load in parallel), so a load-time check would
    // make the same pack pass or fail depending on which page finished first. A mismatch means the strike would
    // be shaped as one element and reduced as another; it warns and the strike keeps its declared type.
    public static void checkDeclaration(List<Either<Holder<Element>, TagKey<Element>>> elements, Holder<DamageType> damageType) {
        for (Either<Holder<Element>, TagKey<Element>> entry : elements) {
            Holder<Element> element = entry.left().orElse(null);
            if (element == null) continue;
            if (element.value().claims(damageType)) continue;
            LOGGER.warn("Element {} does not claim damage type {}, so a strike that declares it is shaped as one element and read back as another", HolderHelper.id(element), HolderHelper.id(damageType));
        }
    }

    // All three fields come from one registry lookup, so a caller cannot end up with the elements of one reading
    // and the origin or the amounts of another. attachment answers "what would this element leave"; only a
    // TYPE reading reaches the buildup, so a roots reading never gets asked.
    public record Strike(Set<Holder<Element>> elements, Map<Holder<Element>, Double> attachment, boolean attaches) {
    }

    // The number belongs to the claim, not to the element, so the index stores this rather than bare holders.
    public record Claim(Holder<Element> element, double attachment) {
    }
}
