package com.iafenvoy.mxt.render.accessory;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import java.util.concurrent.Executor;

/** Reloads both accessory definition folders as one client resource listener. */
public final class AccessoryRenderReloadListener implements PreparableReloadListener {
    @Override
    public java.util.concurrent.CompletableFuture<Void> reload(SharedState state, Executor backgroundExecutor,
                                                                PreparationBarrier barrier, Executor gameExecutor) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            AccessoryRenderDefinitions.reload(state.resourceManager());
            return (Void) null;
        }, backgroundExecutor).thenCompose(barrier::wait);
    }
}
