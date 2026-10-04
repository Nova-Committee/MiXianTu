package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;

/**
 * Where a body stands in a realm chain. The server cache is the only walk of the chain: with no cache (a client, or
 * before a world is loaded) the definitions' own {@code next_realm} links answer instead, which is a different
 * question from a chain the cache refused - a stage the cache does not hold has no rank, not a re-walked one.
 */
public final class CultivationRanks {
    // Bounds a definition-only walk the same way the cache bounds its own.
    private static final int MAX_CHAIN_LENGTH = 1024;

    private CultivationRanks() {
    }

    public static int realmRank(CultivationAttachment spirit, Holder<Aura> aura) {
        ServerCache cache = ServerCache.get().orElse(null);
        Holder<RealmStage> current = spirit.realmStage(aura);
        if (current == null) {
            // A null realm stage represents a mortal, whose formulas still use the
            // chain's first realm as the pending cultivation stage.
            return aura.value().firstRealm()
                    .map(first -> cache == null ? 0 : cache.rankForRealm(HolderHelper.id(first)).orElse(0))
                    .orElse(-1);
        }
        if (cache != null) return cache.rankForRealm(HolderHelper.id(current)).orElse(-1);
        Holder<RealmStage> stage = aura.value().firstRealm().orElse(null);
        for (int rank = 0; stage != null && rank < MAX_CHAIN_LENGTH; rank++) {
            if (stage.equals(current)) return rank;
            stage = stage.value().nextRealm().orElse(null);
        }
        return -1;
    }
}
