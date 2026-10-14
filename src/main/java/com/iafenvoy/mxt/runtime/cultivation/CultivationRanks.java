package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.runtime.DatapackCache;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;

/**
 * Where a body stands in a realm chain. The server cache is the only walk of the chain: with no cache (a client, or
 * before a world is loaded) the definitions' own {@code next_realm} links answer instead, which is a different
 * question from a chain the cache refused - a stage the cache does not hold has no rank, not a re-walked one.
 */
public final class CultivationRanks {
    private CultivationRanks() {
    }

    public static int realmRank(CultivationAttachment spirit, Holder<Aura> aura) {
        DatapackCache cache = DatapackCache.get();
        Holder<RealmStage> current = spirit.realmStage(aura);
        if (current == null) {
            // A null realm stage represents a mortal, whose formulas still use the
            // chain's first realm as the pending cultivation stage.
            return aura.value().firstRealm()
                    .map(first -> cache.rankForRealm(first).orElse(0))
                    .orElse(-1);
        }
        return cache.rankForRealm(current).orElse(-1);
    }
}
