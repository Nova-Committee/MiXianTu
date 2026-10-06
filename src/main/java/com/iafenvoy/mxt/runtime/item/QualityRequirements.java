package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.util.ClientLevelAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The one place a tier requirement is answered, so every place that takes an item asks the same question. The tier
 * comes from the same read the tooltip and the use gate do; the minimum is a ladder comparison, which
 * {@link QualityLadders} owns, so a requirement can never order two tiers differently from everything else.
 *
 * <p>Java-side entry point for a machine slot; the data-pack-facing adapters are the {@code mxt:item_quality}
 * condition, the {@code mxt:quality} matcher entry and the {@code mxt:quality} ingredient.
 */
public final class QualityRequirements {
    private QualityRequirements() {
    }

    public static boolean test(Provider access, ItemStack stack, QualityRequirement requirement) {
        // Nothing asked: every stack passes, including one whose tier nothing declares.
        if (requirement.isEmpty()) return true;
        RegistryLookup<ItemQuality> registry = QualityService.registry(access).orElse(null);
        Holder<ItemQuality> quality = QualityService.find(registry, access, stack).orElse(null);
        if (quality == null || !requirement.admits(quality)) return false;
        Holder<ItemQuality> minimum = requirement.minQuality().orElse(null);
        // A ladder position is not written on the stack, so only the indexed chain can answer it.
        return minimum == null || QualityLadders.atLeast(access, quality, minimum);
    }

    /**
     * The registry access a matching path can reach: the running server, else the client's own level. A ladder is
     * walked out of the synced registry, so a client answers a minimum exactly as the server does; null only where
     * no level is loaded at all, and a reader handed null cannot answer a requirement.
     */
    public static @Nullable Provider access() {
        Provider server = serverAccess();
        return server != null ? server
                : FMLEnvironment.getDist() == Dist.CLIENT ? ClientLevelAccess.level() : null;
    }

    // Null when no server is up.
    private static @Nullable Provider serverAccess() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }
}
