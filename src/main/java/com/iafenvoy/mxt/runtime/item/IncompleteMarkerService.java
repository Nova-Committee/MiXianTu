package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.item.ItemStack;

/**
 * The one answer to "is this stack marked unfinished", so the badge a client draws over a slot and any other reader
 * agree. Nothing is cached: a declaration may read the stack (a tier, an ingredient), so an answer held per item
 * would be wrong, and the table a pack writes is small enough to walk.
 */
public final class IncompleteMarkerService {
    private IncompleteMarkerService() {
    }

    public static boolean marked(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Read without throwing: the registry is synced, and a client draws frames before it has received it.
        return MxtDatapackRegistries.holdersOrEmpty(access, MxtResourceKeys.INCOMPLETE)
                .anyMatch(holder -> ItemMatcher.matches(holder.value(), stack));
    }
}
