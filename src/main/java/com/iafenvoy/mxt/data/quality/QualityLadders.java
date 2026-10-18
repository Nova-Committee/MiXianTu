package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.cache.ChainCache;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.util.chain.Chain;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The one place a quality ladder is read. A ladder is not declared anywhere: it is walked out of the tiers' own
 * {@code next} links, the same way a progression chain is walked out of {@code next_level}, and its name is the
 * {@code quality} some tier on it writes. That name reaches the tiers below it, so a ladder only has to be named
 * once; the tier nothing points at is the entry, and every tier after it is one step higher.
 *
 * <p>The ladder itself is a {@link Chain} of tiers, read in either direction; this class only says
 * which links belong to a ladder, what each ladder is called, which of them are mistakes, and how two tiers compare
 * on the one ladder that holds them both.
 */
public final class QualityLadders {
    private QualityLadders() {
    }

    /**
     * The whole index, for a caller that reads a tier's neighbours rather than the ladder itself. The walk is the one
     * DatapackCache keeps; the fallback answers before a tag update has built one, and reads empty with no registry.
     */
    public static ChainCache<ItemQuality> cache() {
        return RegistryCaches.QUALITY_CHAIN;
    }

    /**
     * The ladder the tier sits on: the one its own name points at. Empty for a tier on no ladder.
     */
    public static Optional<Chain<ItemQuality>> of(@Nullable Holder<ItemQuality> tier) {
        return tier == null ? Optional.empty() : cache().chainOf(tier);
    }

    /**
     * Where a tier stands on its ladder, counted from that ladder's entry tier. Empty for a tier no walked ladder
     * holds, which is both a tier this pack does not provide and every tier of a ladder the walk could not index.
     */
    public static OptionalInt rank(Holder<ItemQuality> tier) {
        Optional<Chain<ItemQuality>> ladder = cache().chainOf(tier);
        return ladder.map(itemQualityChain -> OptionalInt.of(itemQualityChain.indexOf(tier))).orElseGet(OptionalInt::empty);
    }

    /**
     * How far apart two tiers stand on the ladder they share: positive when the first is higher, zero for one tier
     * against itself. Empty when no single walked ladder holds both, since two ladders' positions are unrelated.
     */
    public static OptionalInt compare(Holder<ItemQuality> left, Holder<ItemQuality> right) {
        Optional<Chain<ItemQuality>> ladder = cache().chainOf(left);
        if (ladder.isEmpty()) return OptionalInt.empty();
        int here = ladder.get().indexOf(left);
        // A tier of another ladder, and a tier no ladder holds, both read -1 here: no shared order, not a direction.
        int there = ladder.get().indexOf(right);
        return there < 0 ? OptionalInt.empty() : OptionalInt.of(here - there);
    }

    /**
     * Whether a tier stands at or above a floor on one shared ladder. The only reading the requirement layer uses.
     */
    public static boolean atLeast(Holder<ItemQuality> tier, Holder<ItemQuality> floor) {
        OptionalInt distance = compare(tier, floor);
        return distance.isPresent() && distance.getAsInt() >= 0;
    }

    /**
     * What the ladder the tier sits on is named: the {@code quality} some tier of it writes. Empty for a tier no
     * ladder holds and for a ladder no tier names.
     */
    public static Optional<Identifier> name(Holder<ItemQuality> tier) {
        return of(tier).flatMap(ladder -> ladder.nodes().stream()
                .map(node -> node.value().quality()).flatMap(Optional::stream).findFirst());
    }
}
