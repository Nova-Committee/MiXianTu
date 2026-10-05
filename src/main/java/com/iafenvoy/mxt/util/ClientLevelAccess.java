package com.iafenvoy.mxt.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup.Provider;
import org.jspecify.annotations.Nullable;

/**
 * The client half of a registry access a shared path needs. A class of its own, because the client types it reads
 * are not present on a dedicated server; callers gate it on the distribution first.
 */
public final class ClientLevelAccess {
    public static @Nullable Provider level() {
        return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
    }

    private ClientLevelAccess() {
    }
}
