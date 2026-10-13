package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.DatapackCache;
import com.iafenvoy.mxt.util.ChainCache;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * The one place a quality ladder is read. A ladder is not declared anywhere: it is walked out of the tiers' own
 * {@code next} links, the same way a progression chain is walked out of {@code next_level}, and its name is the
 * {@code quality} some tier on it writes. That name reaches the tiers below it, so a ladder only has to be named
 * once; the tier nothing points at is the entry, and every tier after it is one step higher.
 *
 * <p>The ladder itself is a {@link ChainCache.Chain} of tiers, read in either direction; this class only says
 * which links belong to a ladder, what each ladder is called, which of them are mistakes, and how two tiers compare
 * on the one ladder that holds them both.
 */
public final class QualityLadders {
    private QualityLadders() {
    }

    /**
     * The whole index, for a caller that reads a tier's neighbours rather than the ladder itself. The walk is the one
     * DatapackCache keeps; the fallback answers before a tag update has built it, and reads empty with no registry.
     */
    public static ChainCache<ItemQuality> cache(Provider access) {
        return DatapackCache.get().map(DatapackCache::qualityChains).orElseGet(() -> fallback(access));
    }

    /**
     * The ladder the tier sits on: the one its own name points at. Empty for a tier on no ladder.
     */
    public static Optional<ChainCache.Chain<ItemQuality>> of(Provider access, @Nullable Holder<ItemQuality> tier) {
        return tier == null ? Optional.empty() : cache(access).chainOf(HolderHelper.id(tier));
    }

    /**
     * Where a tier stands on its ladder, counted from that ladder's entry tier. Empty for a tier no walked ladder
     * holds, which is both a tier this pack does not provide and every tier of a ladder the walk refused whole.
     */
    public static OptionalInt rank(Provider access, Holder<ItemQuality> tier) {
        Optional<ChainCache.Chain<ItemQuality>> ladder = cache(access).chainOf(HolderHelper.id(tier));
        return ladder.map(itemQualityChain -> OptionalInt.of(itemQualityChain.indexOf(HolderHelper.id(tier)))).orElseGet(OptionalInt::empty);
    }

    /**
     * How far apart two tiers stand on the ladder they share: positive when the first is higher, zero for one tier
     * against itself. Empty when no single walked ladder holds both, since two ladders' positions are unrelated.
     */
    public static OptionalInt compare(Provider access, Holder<ItemQuality> left, Holder<ItemQuality> right) {
        Optional<ChainCache.Chain<ItemQuality>> ladder = cache(access).chainOf(HolderHelper.id(left));
        if (ladder.isEmpty()) return OptionalInt.empty();
        int here = ladder.get().indexOf(HolderHelper.id(left));
        // A tier of another ladder, and a tier no ladder holds, both read -1 here: no shared order, not a direction.
        int there = ladder.get().indexOf(HolderHelper.id(right));
        return there < 0 ? OptionalInt.empty() : OptionalInt.of(here - there);
    }

    /**
     * Whether a tier stands at or above a floor on one shared ladder. The only reading the requirement layer uses.
     */
    public static boolean atLeast(Provider access, Holder<ItemQuality> tier, Holder<ItemQuality> floor) {
        OptionalInt distance = compare(access, tier, floor);
        return distance.isPresent() && distance.getAsInt() >= 0;
    }

    // Only reached while no tag update has built the cache: memoised per registry instance, the key a reload may keep,
    // which is what ChainCache.invalidate drops.
    private static ChainCache<ItemQuality> fallback(Provider access) {
        return lookup(access).map(registry -> ChainCache.cached(registry, () -> diagnose(access)))
                .orElseGet(ChainCache::empty);
    }

    /**
     * The whole registry walked once, with every problem the ladder rules found riding on the result, so a report can
     * never describe a different registry than the one the runtime reads.
     */
    public static ChainCache<ItemQuality> diagnose(Provider access) {
        return ChainCache.walk(new Ladders(access));
    }

    // The ladder rules: a tier hands over to its next, and one carrying a name that contradicts the name reaching it
    // refuses its whole ladder - the tiers below it are still linked, so they cannot stand as a second ladder.
    private static final class Ladders implements ChainCache.Rules<ItemQuality> {
        private final Map<Identifier, Holder<ItemQuality>> tiers = new LinkedHashMap<>();
        private final Map<Identifier, Identifier> inherited = new LinkedHashMap<>();
        private final List<Identifier> entries = new ArrayList<>();

        private Ladders(Provider access) {
            lookup(access).ifPresent(registry -> registry.listElements()
                    .forEach(holder -> this.tiers.put(holder.key().identifier(), holder)));
            this.inherited.putAll(inherit(this.tiers));
        }

        @Override
        public Map<Identifier, Holder<ItemQuality>> nodes() {
            return this.tiers;
        }

        @Override
        public void link(Identifier node, Holder<ItemQuality> value, Map<Identifier, Holder<ItemQuality>> nodes,
                         ChainCache.Builder<ItemQuality> lines) {
            Identifier declared = value.value().quality().orElse(null);
            if (declared != null && !Objects.equals(declared, this.inherited.get(node)))
                lines.refuse(node, "names quality " + declared + " inside " + this.inherited.get(node));
            value.value().next().map(HolderHelper::id).ifPresent(next -> lines.link(node, next));
        }

        // The entry is the tier nothing points at, and the name reaching it is what the ladder is known by.
        @Override
        public void heads(Map<Identifier, Holder<ItemQuality>> nodes, Set<Identifier> pointedAt,
                          ChainCache.Builder<ItemQuality> lines) {
            for (Identifier node : nodes.keySet()) {
                if (pointedAt.contains(node)) continue;
                this.entries.add(node);
                lines.head(node, this.inherited.get(node));
            }
        }

        @Override
        public void after(ChainCache<ItemQuality> lines, Map<Identifier, Holder<ItemQuality>> nodes,
                          List<ChainCache.Report> reports) {
            // Two entries naming one ladder would leave "where does this climb to" unanswered.
            Set<Identifier> named = new LinkedHashSet<>();
            for (Identifier entry : this.entries) {
                Identifier quality = this.inherited.get(entry);
                if (quality == null || named.add(quality)) continue;
                reports.add(new ChainCache.Report(entry,
                        "starts a second ladder named " + quality + ", which another tier already names"));
            }
            // A tier that names a ladder has to end up on it, whether the walk failed or it was never an entry.
            for (Map.Entry<Identifier, Holder<ItemQuality>> tier : nodes.entrySet())
                if (tier.getValue().value().quality().isPresent() && !lines.contains(tier.getKey()))
                    reports.add(new ChainCache.Report(tier.getKey(),
                            "cannot be reached from the start of its ladder: it is cyclic, points into another, "
                                    + "or names a quality that is already taken"));
        }
    }

    // A ladder is named on one tier and reaches every tier below it, so a pack writes it once. A tier carrying two
    // different names from the tiers above it keeps the first one; a cycle stops the name at the repeat rather than
    // looping forever, so the walk is left to report it.
    private static Map<Identifier, Identifier> inherit(Map<Identifier, Holder<ItemQuality>> tiers) {
        Map<Identifier, Identifier> inherited = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Holder<ItemQuality>> entry : tiers.entrySet()) {
            Identifier quality = entry.getValue().value().quality().orElse(null);
            if (quality == null) continue;
            Identifier current = entry.getKey();
            Set<Identifier> visited = new LinkedHashSet<>();
            while (current != null && visited.add(current)) {
                inherited.putIfAbsent(current, quality);
                Holder<ItemQuality> tier = tiers.get(current);
                if (tier == null) break;
                Identifier next = tier.value().next().map(HolderHelper::id).orElse(null);
                if (next == null || !tiers.containsKey(next)) break;
                current = next;
            }
        }
        return inherited;
    }

    private static Optional<RegistryLookup<ItemQuality>> lookup(Provider access) {
        return access.lookup(MxtResourceKeys.ITEM_QUALITY).map(registry -> (RegistryLookup<ItemQuality>) registry);
    }
}
