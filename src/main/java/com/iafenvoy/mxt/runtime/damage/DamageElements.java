package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.cultivation.DamageTypeClaim;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reverse index from a damage type to the elements claiming it, and the single answer both damage layers
 * read ({@code research/29}). A type nobody claims falls back to the attacker's roots, which carry no element
 * when there is no attacker.
 *
 * <p>A strike's element has to be readable off its {@link DamageSource}: that is all the reduction layer is
 * handed. An {@link Origin#TYPE} strike also leaves its element on the target, a {@link Origin#ROOTS} one only
 * reduces what it deals - which is why an elemental reaction is only ever started by a declared strike.
 */
public final class DamageElements {
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final int REPORT_LIMIT = 128;
    private static final Object LOCK = new Object();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static volatile Map<Registry<DamageType>, Map<Holder<DamageType>, List<Claim>>> indexes = Map.of();

    private DamageElements() {
    }

    // Empty when no element claims the type.
    public static Set<Holder<Element>> of(RegistryAccess access, Holder<DamageType> type) {
        return of(access.lookupOrThrow(MxtResourceKeys.ELEMENT), access.lookupOrThrow(Registries.DAMAGE_TYPE), type);
    }

    // A caller with no running server (a client-side script) gets no element rather than an exception: a
    // harmless query must not be able to bring down a client.
    public static Set<Holder<Element>> of(DamageSource source) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Set.of();
        return of(server.registryAccess(), source.typeHolder());
    }

    // The single rule the pipeline and the damage condition both read, so a condition can never disagree with
    // the number that was applied. The origin is dropped - use reading(...) when the two must be told apart.
    public static Set<Holder<Element>> strike(Level level, Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        return reading(level, type, attacker).elements();
    }

    // strike(...) with the origin and the amounts kept: the reduction step wants the elements, the buildup step
    // also wants how much of each this kind of hit leaves.
    public static Strike reading(Level level, Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        RegistryAccess access = level.registryAccess();
        List<Claim> claimed = type.map(holder -> claims(access.lookupOrThrow(MxtResourceKeys.ELEMENT),
                access.lookupOrThrow(Registries.DAMAGE_TYPE), holder)).orElse(List.of());
        return claimed.isEmpty() ? roots(attacker) : of(claimed);
    }

    // What the reduction and attachment steps of the incoming event read.
    public static Strike reading(DamageSource source) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            RegistryAccess access = server.registryAccess();
            List<Claim> claimed = claims(access.lookupOrThrow(MxtResourceKeys.ELEMENT),
                    access.lookupOrThrow(Registries.DAMAGE_TYPE), source.typeHolder());
            if (!claimed.isEmpty()) return of(claimed);
        }
        return roots(source.getEntity());
    }

    // Nobody claimed the type, so the attacker's own roots answer. The amounts are the elements' own defaults,
    // which only matter to a caller that ignores the origin - the buildup step does not.
    private static Strike roots(@Nullable Entity attacker) {
        Set<Holder<Element>> elements = attacker == null ? Set.of() : Elements.of(attacker);
        return new Strike(elements, amounts(elements), Origin.ROOTS);
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
        return new Strike(Set.copyOf(elements), Map.copyOf(attachment), Origin.TYPE);
    }

    // What the reduction layer is handed. The causing entity is the attacker a source is credited with, so a
    // plain mob swing claiming no damage type still reads the mob's own elements.
    public static Set<Holder<Element>> strike(DamageSource source) {
        return reading(source).elements();
    }

    // Only two answers are observable from a DamageSource: the type was claimed, or nobody claimed it and the
    // attacker's roots answered. A declared element resolves to a type before it travels, so a weapon's element
    // arrives here as a claim like any environmental type.
    public enum Origin {
        // A damage type names these elements, so the strike really is made of them: they reduce and they build
        // up on the target.
        TYPE(true),
        // Nobody claimed the type, so the attacker's roots answered: a body's own element reduces what it deals
        // but does not rub off on whoever it hits.
        ROOTS(false);

        private final boolean attaches;

        Origin(boolean attaches) {
            this.attaches = attaches;
        }

        // Whether a strike read from here leaves anything on the target.
        public boolean attaches() {
            return this.attaches;
        }
    }

    // All three fields come from one registry lookup, so a caller cannot end up with the elements of one reading
    // and the origin or the amounts of another. attachment answers "what would this element leave"; only a
    // TYPE reading reaches the buildup, so a roots reading never gets asked.
    public record Strike(Set<Holder<Element>> elements, Map<Holder<Element>, Double> attachment, Origin origin) {
    }

    // The number belongs to the claim, not to the element, so the index stores this rather than bare holders.
    public record Claim(Holder<Element> element, double attachment) {
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
    public static Optional<Holder<DamageType>> resolveType(RegistryAccess access,
                                                           List<Either<Holder<Element>, TagKey<Element>>> elements,
                                                           Optional<Holder<DamageType>> damageType) {
        if (damageType.isPresent()) {
            if (!elements.isEmpty()) checkDeclaration(access, elements, damageType.get());
            return damageType;
        }
        if (elements.isEmpty()) return Optional.empty();
        Optional<Holder<Element>> element = elements.stream().flatMap(entry -> entry.left().stream()).findFirst();
        if (element.isEmpty()) {
            report("A tagged element cannot name the damage type of a strike; declare damage_type as well");
            return Optional.empty();
        }
        Optional<Holder<DamageType>> resolved = typeOf(access, element.get());
        if (resolved.isEmpty())
            report("Element " + HolderHelper.id(element.get())
                    + " claims no damage type, so a strike that declares it has no element of its own");
        return resolved;
    }

    // Checked on first use, not at load: the value behind a declared element is not necessarily bound yet while
    // another datapack registry page is being decoded (registries load in parallel), so a load-time check would
    // make the same pack pass or fail depending on which page finished first. A mismatch means the strike would
    // be shaped as one element and reduced as another; it warns and the strike keeps its declared type.
    public static void checkDeclaration(RegistryAccess access, List<Either<Holder<Element>, TagKey<Element>>> elements,
                                        Holder<DamageType> damageType) {
        for (Either<Holder<Element>, TagKey<Element>> entry : elements) {
            Holder<Element> element = entry.left().orElse(null);
            if (element == null) continue;
            if (element.value().claims(damageType)) continue;
            report("Element " + HolderHelper.id(element) + " does not claim damage type " + HolderHelper.id(damageType)
                    + ", so a strike that declares it is shaped as one element and read back as another");
        }
    }

    // A mismatch is a pack mistake that would otherwise repeat on every strike; REPORT_LIMIT caps the noise.
    private static void report(String message) {
        if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(message)) MiXianTu.LOGGER.warn("{}", message);
    }

    // Called on every datapack load, before anything can rebuild the index.
    public static void invalidate() {
        synchronized (LOCK) {
            indexes = Map.of();
        }
    }

    private static List<Claim> claims(Registry<Element> elements, Registry<DamageType> types, Holder<DamageType> type) {
        List<Claim> claimed = index(elements, types).get(type);
        return claimed == null ? List.of() : claimed;
    }

    private static Set<Holder<Element>> of(Registry<Element> elements, Registry<DamageType> types, Holder<DamageType> type) {
        List<Claim> claimed = claims(elements, types, type);
        if (claimed.isEmpty()) return Set.of();
        Set<Holder<Element>> result = new LinkedHashSet<>();
        for (Claim claim : claimed) result.add(claim.element());
        return Set.copyOf(result);
    }

    // Keyed on the damage type registry instance, and dropped on every datapack load by ServerCache: a reloaded
    // pack may keep the same registry instance, so the key alone cannot be trusted to notice one. The index
    // reads element definitions, so it never outlives the pack that built it. MAX_CACHED_REGISTRIES bounds the map.
    private static Map<Holder<DamageType>, List<Claim>> index(Registry<Element> elements, Registry<DamageType> types) {
        Map<Holder<DamageType>, List<Claim>> cached = indexes.get(types);
        if (cached != null) return cached;
        synchronized (LOCK) {
            cached = indexes.get(types);
            if (cached != null) return cached;
            Map<Holder<DamageType>, List<Claim>> built = new HashMap<>();
            for (Reference<Element> element : elements.listElements().toList()) {
                if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element)) continue;
                claimAll(built, types, element);
            }
            Map<Holder<DamageType>, List<Claim>> frozen = new HashMap<>();
            built.forEach((type, claimants) -> frozen.put(type, List.copyOf(claimants)));
            Map<Registry<DamageType>, Map<Holder<DamageType>, List<Claim>>> updated =
                    indexes.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(indexes);
            updated.put(types, Map.copyOf(frozen));
            indexes = Map.copyOf(updated);
            return updated.get(types);
        }
    }

    private static void claimAll(Map<Holder<DamageType>, List<Claim>> index, Registry<DamageType> types, Holder<Element> element) {
        for (DamageTypeClaim claim : element.value().damageTypes()) {
            // The claim's own number when it wrote one, the element's own default otherwise.
            double attachment = claim.attachment(element.value().damageAttachment());
            claim.type().left().ifPresentOrElse(
                    type -> claim(index, type, element, attachment),
                    () -> {
                        TagKey<DamageType> tag = claim.type().right().orElseThrow();
                        types.listElements().filter(type -> type.is(tag))
                                .forEach(type -> claim(index, type, element, attachment));
                    });
        }
    }

    private static void claim(Map<Holder<DamageType>, List<Claim>> index, Holder<DamageType> type, Holder<Element> element,
                              double attachment) {
        List<Claim> claimants = index.computeIfAbsent(type, ignored -> new ArrayList<>());
        for (Claim existing : claimants) if (existing.element().equals(element)) return;
        // Every claim multiplies downstream, so a second claimant for one type is worth a warning.
        if (!claimants.isEmpty())
            MiXianTu.LOGGER.warn("Damage type {} is claimed by more than one element; every claim multiplies the element relation of such a strike",
                    HolderHelper.id(type));
        claimants.add(new Claim(element, attachment));
    }
}
