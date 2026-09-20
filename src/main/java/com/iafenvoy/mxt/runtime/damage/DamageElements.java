package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.MiXianTu;
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
    private static volatile Map<Registry<DamageType>, Map<Holder<DamageType>, List<Holder<Element>>>> indexes = Map.of();

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
     * The elements one strike belongs to: the damage type's claimants when it has any, and the attacker's
     * spirit-root elements when it does not. This is the single rule both layers of the damage pipeline and the
     * element damage condition read, so a condition can never disagree with the number the pipeline applied.
     */
    public static Set<Holder<Element>> strike(Level level, Optional<Holder<DamageType>> type, @Nullable Entity attacker) {
        Set<Holder<Element>> claimed = type.map(holder -> of(level.registryAccess(), holder)).orElse(Set.of());
        if (!claimed.isEmpty()) return claimed;
        return attacker == null ? Set.of() : Elements.of(attacker);
    }

    /**
     * {@link #strike(Level, Optional, Entity)} for a source that already exists, which is what the reduction
     * layer is handed. The causing entity is the attacker a source is credited with, so a plain mob swing that
     * claims no damage type still reads the mob's own elements.
     */
    public static Set<Holder<Element>> strike(DamageSource source) {
        Set<Holder<Element>> claimed = of(source);
        if (!claimed.isEmpty()) return claimed;
        Entity attacker = source.getEntity();
        return attacker == null ? Set.of() : Elements.of(attacker);
    }

    /**
     * The damage type an element's own claim resolves to: the first claim it lists that exists, which is what
     * lets a damage action say "this strike is fire" and still have a damage type to travel as. An element that
     * claims nothing has no answer here, and {@link #resolveType} says so once for whoever needed one.
     */
    public static Optional<Holder<DamageType>> typeOf(RegistryAccess access, Holder<Element> element) {
        Registry<DamageType> types = access.lookupOrThrow(Registries.DAMAGE_TYPE);
        for (Either<Holder<DamageType>, TagKey<DamageType>> entry : element.value().damageTypes()) {
            if (entry.left().isPresent()) return entry.left();
            TagKey<DamageType> tag = entry.right().orElseThrow();
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

    private static Set<Holder<Element>> of(Registry<Element> elements, Registry<DamageType> types, Holder<DamageType> type) {
        List<Holder<Element>> claimed = index(elements, types).get(type);
        return claimed == null ? Set.of() : Set.copyOf(claimed);
    }

    private static Map<Holder<DamageType>, List<Holder<Element>>> index(Registry<Element> elements, Registry<DamageType> types) {
        Map<Holder<DamageType>, List<Holder<Element>>> cached = indexes.get(types);
        if (cached != null) return cached;
        synchronized (LOCK) {
            cached = indexes.get(types);
            if (cached != null) return cached;
            Map<Holder<DamageType>, List<Holder<Element>>> built = new HashMap<>();
            for (Reference<Element> element : elements.listElements().toList()) {
                if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element)) continue;
                claimAll(built, types, element);
            }
            Map<Holder<DamageType>, List<Holder<Element>>> frozen = new HashMap<>();
            built.forEach((type, claimants) -> frozen.put(type, List.copyOf(claimants)));
            Map<Registry<DamageType>, Map<Holder<DamageType>, List<Holder<Element>>>> updated =
                    indexes.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(indexes);
            updated.put(types, Map.copyOf(frozen));
            indexes = Map.copyOf(updated);
            return updated.get(types);
        }
    }

    private static void claimAll(Map<Holder<DamageType>, List<Holder<Element>>> index, Registry<DamageType> types, Holder<Element> element) {
        for (Either<Holder<DamageType>, TagKey<DamageType>> entry : element.value().damageTypes()) {
            entry.left().ifPresentOrElse(
                    type -> claim(index, type, element),
                    () -> {
                        TagKey<DamageType> tag = entry.right().orElseThrow();
                        types.listElements().filter(type -> type.is(tag)).forEach(type -> claim(index, type, element));
                    });
        }
    }

    private static void claim(Map<Holder<DamageType>, List<Holder<Element>>> index, Holder<DamageType> type, Holder<Element> element) {
        List<Holder<Element>> claimants = index.computeIfAbsent(type, ignored -> new ArrayList<>());
        if (claimants.contains(element)) return;
        if (!claimants.isEmpty())
            MiXianTu.LOGGER.warn("Damage type {} is claimed by more than one element; every claim multiplies the element relation of such a strike",
                    HolderHelper.id(type));
        claimants.add(element);
    }
}
