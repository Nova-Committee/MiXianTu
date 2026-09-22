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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which elements one strike belongs to, read from its damage type.
 *
 * <p>An element claims damage types ({@code Element#damageTypes}), and a claim is a definition rather than a
 * mapping table: it is what says that a fireball, a lava bath and a burning blade are the same thing. This
 * class is the reading side - the reverse index from a damage type to the elements that speak for it - and it
 * is the reason the reduction layer can be elemental at all, because the only thing that layer is handed is a
 * {@link DamageSource}. The source's own {@code typeHolder()} is therefore enough: no per-strike state has to
 * travel between the two layers, and a hit this mod never dealt (a lava tick, a lightning bolt, another mod's
 * sword) is read exactly like one of ours.</p>
 *
 * <p>A damage type nobody claims leaves a strike with the reading it had before elements could claim anything:
 * the attacker's spirit-root elements, which is what the index falls back to in {@link #strike}. An unclaimed
 * type with no attacker therefore carries no element at all, which is the honest answer for a fall or a
 * cactus.</p>
 *
 * <p>Which of those two answers a strike got is kept as an {@link Origin}, because the two are not
 * interchangeable downstream: a claimed strike leaves its element on the target, while a body's own element
 * only reduces what it deals. Reading both halves in one call is what stops a caller from pairing the elements
 * of one answer with the origin of the other.</p>
 *
 * <p>The index is rebuilt when the damage type registry instance changes, which a data pack reload does: the
 * element registry is reloaded in the same step, so keying on one of the two is enough to notice both. A
 * damage type claimed by several elements keeps all of them and says so once, because every claim then
 * multiplies - the same rule several spirit roots already follow.</p>
 */
public final class DamageElements {
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final int REPORT_LIMIT = 128;
    private static final Object LOCK = new Object();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static volatile Map<Registry<DamageType>, Map<Holder<DamageType>, List<Claim>>> indexes = Map.of();

    private DamageElements() {
    }

    /**
     * The elements that speak for this damage type, empty when none claims it.
     */
    public static Set<Holder<Element>> of(RegistryAccess access, Holder<DamageType> type) {
        return of(access.lookupOrThrow(MxtResourceKeys.ELEMENT), access.lookupOrThrow(Registries.DAMAGE_TYPE), type);
    }

    /**
     * The same reading taken from the running server's registries, for the callers that only hold a
     * {@link DamageSource} - the incoming-damage event and the damage conditions. A caller with no server to
     * ask (a client-side script) gets no element rather than an exception: the alternative would let a
     * harmless query bring down a client.
     */
    public static Set<Holder<Element>> of(DamageSource source) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Set.of();
        return of(server.registryAccess(), source.typeHolder());
    }

    /**
     * The elements one strike belongs to, the damage type's claimants when it has any, and the attacker's
     * spirit-root elements when it does not. This is the single rule both layers of the damage pipeline and the
     * element damage condition read, so a condition can never disagree with the number the pipeline applied.
     *
     * <p>The origin is dropped here; callers that have to tell the two apart - the attachment step does - take
     * {@link #reading(Level, Optional, Entity)} instead.</p>
     */
    public static Set<Holder<Element>> strike(Level level, Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        return reading(level, type, attacker).elements();
    }

    /**
     * {@link #strike(Level, Optional, Entity)} with the origin and the amounts kept, for callers that treat the
     * readings differently - the reduction step takes the elements, the buildup step also needs to know how much
     * of each this kind of hit leaves.
     */
    public static Strike reading(Level level, Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        RegistryAccess access = level.registryAccess();
        List<Claim> claimed = type.map(holder -> claims(access.lookupOrThrow(MxtResourceKeys.ELEMENT),
                access.lookupOrThrow(Registries.DAMAGE_TYPE), holder)).orElse(List.of());
        return claimed.isEmpty() ? roots(attacker) : of(claimed);
    }

    /**
     * {@link #strike(DamageSource)} with the origin and the amounts kept, which is what the reduction and the
     * attachment steps of the incoming event read.
     */
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

    /**
     * The fallback reading: nobody claimed the type, so the attacker's own roots answer. The amounts are the
     * elements' own defaults, which only ever matter if a caller ignores the origin - the buildup step does not.
     */
    private static Strike roots(@Nullable Entity attacker) {
        Set<Holder<Element>> elements = attacker == null ? Set.of() : Elements.of(attacker);
        return new Strike(elements, amounts(elements), Origin.ROOTS);
    }

    /**
     * What each of these elements would leave behind by its own declaration.
     */
    private static Map<Holder<Element>, Double> amounts(Set<Holder<Element>> elements) {
        Map<Holder<Element>, Double> attachment = new LinkedHashMap<>();
        for (Holder<Element> element : elements) attachment.put(element, element.value().damageAttachment());
        return Map.copyOf(attachment);
    }

    /**
     * The claimed reading: one entry per element, in the order the index lists them, with the amount its claim
     * over this type carries. The first claim an element makes over a type is the one that speaks, which is the
     * same reading the index itself keeps.
     */
    private static Strike of(List<Claim> claimed) {
        Set<Holder<Element>> elements = new LinkedHashSet<>();
        Map<Holder<Element>, Double> attachment = new LinkedHashMap<>();
        for (Claim claim : claimed)
            if (elements.add(claim.element())) attachment.put(claim.element(), claim.attachment());
        return new Strike(Set.copyOf(elements), Map.copyOf(attachment), Origin.TYPE);
    }

    /**
     * {@link #strike(Level, Optional, Entity)} for a source that already exists, which is what the reduction
     * layer is handed. The causing entity is the attacker a source is credited with, so a plain mob swing that
     * claims no damage type still reads the mob's own elements.
     */
    public static Set<Holder<Element>> strike(DamageSource source) {
        return reading(source).elements();
    }

    /**
     * Where a strike's elements were read from, which decides whether they rub off.
     *
     * <p>Only two answers are observable from a {@link DamageSource}: the type was claimed, or nobody claimed it
     * and the attacker's roots answered. A declared element resolves to a damage type before it travels, so a
     * weapon's or an artefact's element arrives here as a claim, exactly like a claimed environmental type.</p>
     */
    public enum Origin {
        /**
         * A damage type names these elements, so the strike really is made of them: they reduce <em>and</em>
         * they build up on the target.
         */
        TYPE(true),
        /**
         * Nobody claimed the type, so the attacker's spirit roots answered: a body's own element reduces what
         * it deals but does not rub off on whoever it hits. This is what keeps "the fire in my blood" and "the
         * fire in my blade" apart, and it is why an elemental reaction is only ever started by a strike that
         * declared what it was.
         */
        ROOTS(false);

        private final boolean attaches;

        Origin(boolean attaches) {
            this.attaches = attaches;
        }

        /**
         * Whether a strike read from here leaves anything on the target.
         */
        public boolean attaches() {
            return this.attaches;
        }
    }

    /**
     * One reading of a strike: what it is made of, where that came from, and how much of each element this kind
     * of hit leaves behind. All three come from one registry lookup, so a caller cannot end up with the elements
     * of one reading and the origin or the amounts of another.
     *
     * <p>{@code attachment} is populated for both origins and answers "what would this element leave"; only a
     * {@link Origin#TYPE} reading is handed to the buildup, so a roots reading simply never gets asked. An
     * element whose claim wrote its own number reports that number, and one that did not reports the element's
     * own {@code damage_attachment}.</p>
     */
    public record Strike(Set<Holder<Element>> elements, Map<Holder<Element>, Double> attachment, Origin origin) {
    }

    /**
     * One element's claim over one damage type, with the amount a strike of that type leaves behind already
     * resolved against the element's own default. The index stores these rather than bare holders because the
     * number belongs to the claim, not to the element.
     */
    public record Claim(Holder<Element> element, double attachment) {
    }

    /**
     * The damage type an element's own claim resolves to: the first claim it lists that exists, which is what
     * lets a damage action say "this strike is fire" and still have a damage type to travel as. An element that
     * claims nothing has no answer here, and {@link #resolveType} says so once for whoever needed one.
     */
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

    /**
     * The damage type a damage action's declaration travels as, resolved where registries can be read: the
     * declared type (with the declaration checked against it), or the first type the declared element claims.
     *
     * <p>An empty result means the strike keeps the reading it always had - whatever the attacker's roots are.
     * Every way a declaration can fail to name a type is reported once here rather than failing the load: a
     * declaration names only tags (no single type to travel as), the named element claims no damage type, or the
     * declared type is not one the element claims.</p>
     */
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

    /**
     * Checks a damage action's element declaration against the damage type the strike will travel as, and says
     * so when the two do not agree.
     *
     * <p>The rule is the one the whole element channel rests on: the reduction layer only ever sees a
     * {@link DamageSource}, so the element of a strike has to be readable from its damage type. A declaration
     * the type does not support is therefore a strike that would be shaped as one element and reduced as
     * another.</p>
     *
     * <p>It is checked on first use rather than while the pack loads, because it cannot be checked there: the
     * value behind a declared element is not necessarily bound yet when another datapack registry page is being
     * decoded (registries load in parallel), so reading it at load time would make the same pack pass or fail
     * depending on which page happened to finish first. Each distinct complaint is reported once, and the run
     * continues: the strike keeps the damage type it declared, and only the declaration is wrong.</p>
     */
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

    /**
     * Reports the same complaint once. A mismatch is a data-pack mistake that would otherwise repeat on every
     * strike, and one line is enough to find it.
     */
    private static void report(String message) {
        if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(message)) MiXianTu.LOGGER.warn("{}", message);
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
        if (!claimants.isEmpty())
            MiXianTu.LOGGER.warn("Damage type {} is claimed by more than one element; every claim multiplies the element relation of such a strike",
                    HolderHelper.id(type));
        claimants.add(new Claim(element, attachment));
    }
}
