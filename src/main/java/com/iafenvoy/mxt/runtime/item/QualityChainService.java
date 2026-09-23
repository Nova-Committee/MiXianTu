package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The one place a quality ladder is read. Where a tier sits, what comes next and whether an item belongs to the
 * chain are all answered here, so the resolution order, the membership gate and the upgrade service cannot drift
 * apart on what "the next tier" means.
 */
public final class QualityChainService {
    private QualityChainService() {
    }

    public static Optional<Holder<QualityChain>> chain(Provider access, Identifier id) {
        return lookup(access).flatMap(registry -> registry.get(ResourceKey.create(MxtResourceKeys.QUALITY_CHAIN, id)))
                .map(holder -> holder);
    }

    // Every enabled chain holding this tier. More than one means the tier alone does not say where it climbs.
    public static List<Holder<QualityChain>> chainsOf(Provider access, @Nullable Holder<ItemQuality> quality) {
        if (quality == null) return List.of();
        return lookup(access).map(registry -> registry.listElements()
                .filter(chain -> !MxtDatapackRegistries.isDisabled(MxtResourceKeys.QUALITY_CHAIN, chain))
                .filter(chain -> chain.value().isMember(quality))
                .map(chain -> (Holder<QualityChain>) chain)
                .toList()).orElse(List.of());
    }

    public static Optional<Holder<QualityChain>> soleChain(Provider access, @Nullable Holder<ItemQuality> quality) {
        List<Holder<QualityChain>> chains = chainsOf(access, quality);
        return chains.size() == 1 ? Optional.of(chains.getFirst()) : Optional.empty();
    }

    public static boolean isMember(@Nullable Holder<QualityChain> chain, @Nullable Holder<ItemQuality> quality) {
        return chain != null && chain.value().isMember(quality);
    }

    // An absent chain registry is not an error: a pack that declares no ladder simply has none.
    private static Optional<RegistryLookup<QualityChain>> lookup(Provider access) {
        return access.lookup(MxtResourceKeys.QUALITY_CHAIN).map(registry -> (RegistryLookup<QualityChain>) registry);
    }
}
