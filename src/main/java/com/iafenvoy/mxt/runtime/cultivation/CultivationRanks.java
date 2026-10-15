package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.runtime.DatapackCache;
import net.minecraft.core.Holder;

import java.util.OptionalInt;

/**
 * Where a body stands in a realm chain. Every answer is the shared index's, so a stage that index does not hold has
 * no rank rather than a re-walked one.
 * The number counts the mortal state as zero, so the first realm is one and every stage above it follows suit.
 */
public final class CultivationRanks {
    private CultivationRanks() {
    }

    public static int realmRank(CultivationAttachment spirit, Holder<Aura> aura) {
        DatapackCache cache = DatapackCache.get();
        Holder<RealmStage> current = spirit.realmStage(aura);
        if (current == null) {
            // The mortal state sits one rank below the chain's first realm, and an aura that declares no first
            // realm is off every chain. This does not consult the cache: whether the chain got indexed has no
            // bearing on a body that never entered it, and the progress variables need a usable rank even where
            // no index exists.
            return aura.value().firstRealm().isPresent() ? 0 : -1;
        }
        OptionalInt rank = cache.rankForRealm(current);
        return rank.isPresent() ? rank.getAsInt() + 1 : -1;
    }
}
