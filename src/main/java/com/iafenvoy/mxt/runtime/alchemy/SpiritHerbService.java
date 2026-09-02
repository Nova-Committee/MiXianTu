package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Resolves herb metadata for an existing physical item. Growth,
 * harvesting, and generation are deliberately left to content mods or scripts.
 */
public final class SpiritHerbService {
    private SpiritHerbService() {
    }

    public static Optional<SpiritHerb> find(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtResourceKeys.SPIRIT_HERB)
                .map(Reference::value), stack);
    }

    /**
     * Reads the matching definition from the client-synchronised registry view.
     */
    public static Optional<SpiritHerb> find(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.SPIRIT_HERB)
                .map(Reference::value), stack);
    }
}
