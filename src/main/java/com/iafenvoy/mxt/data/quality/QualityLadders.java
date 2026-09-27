package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The one place a quality ladder is read. A ladder is not declared anywhere: it is walked out of the tiers' own
 * {@code next} links, the same way a skill chain is walked out of {@code next_stage}, and its name is the
 * {@code quality} some tier on it writes. That name reaches the tiers below it, so a ladder only has to be named
 * once; the tier nothing points at is the entry, and every tier after it is one step higher.
 *
 * <p>Indexed lazily from the registry instance and invalidated when a data pack replaces it: the entry point and
 * the ranks are only knowable once every tier of the registry is visible.
 */
public final class QualityLadders {
    private static final int MAX_CACHED_REGISTRIES = 4;

    private static final Object LOCK = new Object();
    private static final Map<RegistryLookup<ItemQuality>, Index> CACHE = new LinkedHashMap<>();

    private QualityLadders() {
    }

    public static void invalidate() {
        synchronized (LOCK) {
            CACHE.clear();
        }
    }

    /**
     * One walked ladder, tiers low to high. {@code quality} is null for a ladder whose tiers name none.
     */
    public record Ladder(@Nullable Identifier quality, List<Holder<ItemQuality>> tiers, Map<Identifier, Integer> ranks) {
        public boolean isMember(@Nullable Holder<ItemQuality> tier) {
            return this.indexOf(tier) >= 0;
        }

        // -1 when the tier is not on this ladder. Compared by id because a holder is not stable across reloads.
        public int indexOf(@Nullable Holder<ItemQuality> tier) {
            return tier == null ? -1 : this.ranks.getOrDefault(HolderHelper.id(tier), -1);
        }

        public Holder<ItemQuality> first() {
            return this.tiers.getFirst();
        }

        // Empty at the top of the ladder, and for a tier this ladder does not hold.
        public Optional<Holder<ItemQuality>> nextTier(@Nullable Holder<ItemQuality> tier) {
            int index = this.indexOf(tier);
            return index < 0 || index + 1 >= this.tiers.size() ? Optional.empty() : Optional.of(this.tiers.get(index + 1));
        }
    }

    /**
     * One ladder as it was walked, or the reason it could not be. The reason is a sentence the data pack report
     * carries as-is; a walk that failed indexes nothing.
     */
    public record Walk(@Nullable Ladder ladder, @Nullable String problem) {
        public boolean ok() {
            return this.ladder != null;
        }
    }

    /**
     * One problem the whole registry shows, naming the tier whose file an author has to open.
     */
    public record Report(Identifier tier, String message) {
    }

    /**
     * One walk per entry tier, with the problems the whole registry shows: where a name never reaches its tiers,
     * where two entries name one ladder, and where a tier cannot be reached from anything.
     */
    public record Diagnosis(Index index, List<Report> problems) {
    }

    public static Walk walk(RegistryLookup<ItemQuality> registry, Identifier entry,
                           Map<Identifier, Identifier> inherited) {
        List<Holder<ItemQuality>> tiers = new ArrayList<>();
        List<Identifier> seen = new ArrayList<>();
        Identifier current = entry;
        while (current != null) {
            if (seen.contains(current)) return new Walk(null, "the ladder is cyclic at " + current);
            ItemQuality tier = value(registry, current);
            if (tier == null) return new Walk(null, "next " + current + " is not a quality");
            // Tiers of one ladder all walk down from the same name, so a second one is a mistake.
            Identifier declared = tier.quality().orElse(null);
            Identifier expected = inherited.get(current);
            if (declared != null && !Objects.equals(declared, expected))
                return new Walk(null, "tier " + current + " names quality " + declared + " inside " + expected);
            seen.add(current);
            tiers.add(holder(registry, current));
            current = tier.next().map(HolderHelper::id).orElse(null);
        }
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < seen.size(); index++) ranks.put(seen.get(index), index);
        Ladder ladder = new Ladder(inherited.get(entry), List.copyOf(tiers), Map.copyOf(ranks));
        return new Walk(ladder, null);
    }

    /**
     * The whole registry walked once: the index reads, plus the problems the walk found. Both come from one pass,
     * so a report can never describe a different registry than the one being read.
     */
    public static Diagnosis diagnose(RegistryLookup<ItemQuality> registry) {
        Map<Identifier, ItemQuality> tiers = new LinkedHashMap<>();
        registry.listElements().forEach(holder -> tiers.put(holder.key().identifier(), holder.value()));
        Set<Identifier> pointedAt = new LinkedHashSet<>();
        for (ItemQuality tier : tiers.values()) tier.next().map(HolderHelper::id).ifPresent(pointedAt::add);
        Map<Identifier, Identifier> inherited = inherit(tiers);
        List<Report> problems = new ArrayList<>();
        Map<Identifier, Ladder> byTier = new LinkedHashMap<>();
        Map<Identifier, Ladder> byQuality = new LinkedHashMap<>();
        Set<Identifier> walked = new LinkedHashSet<>();
        Set<Identifier> named = new LinkedHashSet<>();
        for (Map.Entry<Identifier, ItemQuality> candidate : tiers.entrySet()) {
            Identifier entry = candidate.getKey();
            // Every tier below another tier belongs to that tier's ladder, so only the top of a group starts a
            // walk. A tier whose name never reaches it is reported instead of indexed.
            if (pointedAt.contains(entry) || walked.contains(entry)) continue;
            Identifier quality = candidate.getValue().quality().orElse(null);
            if (quality != null && inherited.get(entry) == null) {
                problems.add(new Report(entry,
                        "its quality " + quality + " never reaches it, because the tier below it names another one"));
                continue;
            }
            Walk result = walk(registry, entry, inherited);
            Ladder ladder = result.ladder();
            if (ladder == null) {
                problems.add(new Report(entry, result.problem()));
                continue;
            }
            // Two entries naming one ladder would leave "where does this climb to" unanswered.
            if (ladder.quality() != null && !named.add(ladder.quality())) {
                problems.add(new Report(entry,
                        "starts a second ladder named " + ladder.quality() + ", which another tier already names"));
                continue;
            }
            for (Holder<ItemQuality> tier : ladder.tiers()) {
                Identifier id = HolderHelper.id(tier);
                byTier.put(id, ladder);
                walked.add(id);
            }
            if (ladder.quality() != null) byQuality.put(ladder.quality(), ladder);
        }
        // A tier that names a ladder has to end up on it, whether the walk failed or it was never an entry.
        for (Map.Entry<Identifier, ItemQuality> entry : tiers.entrySet())
            if (entry.getValue().quality().isPresent() && !byTier.containsKey(entry.getKey()))
                problems.add(new Report(entry.getKey(),
                        "cannot be reached from the start of its ladder: it is cyclic, points into another, "
                                + "or names a quality that is already taken"));
        return new Diagnosis(new Index(Map.copyOf(byTier), Map.copyOf(byQuality)), List.copyOf(problems));
    }

    /**
     * The ladder the tier sits on: the one its own name points at. Empty for a tier on no ladder.
     */
    public static Optional<Ladder> of(Provider access, @Nullable Holder<ItemQuality> tier) {
        if (tier == null) return Optional.empty();
        return lookup(access).map(registry -> index(registry).byTier().get(HolderHelper.id(tier)));
    }

    /**
     * The ladder a name walks into, empty for a name no tier declares.
     */
    public static Optional<Ladder> chain(Provider access, Identifier quality) {
        return lookup(access).map(registry -> index(registry).byQuality().get(quality));
    }

    private static Index index(RegistryLookup<ItemQuality> registry) {
        synchronized (LOCK) {
            Index cached = CACHE.get(registry);
            if (cached != null) {
                // Re-inserting keeps the eviction order least-recently-used.
                CACHE.remove(registry);
                CACHE.put(registry, cached);
                return cached;
            }
        }
        Index built = rebuild(registry);
        synchronized (LOCK) {
            CACHE.put(registry, built);
            while (CACHE.size() > MAX_CACHED_REGISTRIES) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return built;
    }

    // Entries are the tiers no other tier points at. Anything not reachable from one is left out of the index
    // rather than guessed at, because an unreachable prefix has no entry to be ranked from.
    private static Index rebuild(RegistryLookup<ItemQuality> registry) {
        return diagnose(registry).index();
    }

    // A ladder is named on one tier and reaches every tier below it, so a pack writes it once. A tier carrying two
    // different names from the tiers above it keeps the first one and is reported by the walk that crosses it.
    private static Map<Identifier, Identifier> inherit(Map<Identifier, ItemQuality> tiers) {
        Map<Identifier, Identifier> inherited = new LinkedHashMap<>();
        for (Map.Entry<Identifier, ItemQuality> entry : tiers.entrySet()) {
            Identifier quality = entry.getValue().quality().orElse(null);
            if (quality == null) continue;
            Identifier current = entry.getKey();
            while (current != null) {
                inherited.putIfAbsent(current, quality);
                ItemQuality tier = tiers.get(current);
                if (tier == null) break;
                Identifier next = tier.next().map(HolderHelper::id).orElse(null);
                if (next == null || tiers.get(next) == null) break;
                current = next;
            }
        }
        return inherited;
    }

    private static @Nullable ItemQuality value(RegistryLookup<ItemQuality> registry, Identifier id) {
        return registry.get(ResourceKey.create(MxtResourceKeys.ITEM_QUALITY, id)).map(Holder::value).orElse(null);
    }

    // A tier that was walked came out of the registry, so the strict lookup cannot miss.
    private static Holder<ItemQuality> holder(RegistryLookup<ItemQuality> registry, Identifier id) {
        return registry.getOrThrow(ResourceKey.create(MxtResourceKeys.ITEM_QUALITY, id));
    }

    private static Optional<RegistryLookup<ItemQuality>> lookup(Provider access) {
        return access.lookup(MxtResourceKeys.ITEM_QUALITY).map(registry -> (RegistryLookup<ItemQuality>) registry);
    }

    private record Index(Map<Identifier, Ladder> byTier, Map<Identifier, Ladder> byQuality) {
    }
}
