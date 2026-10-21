package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.function.Supplier;

@EventBusSubscriber(Dist.CLIENT)
public final class ClientRenderResources {
    private static final CloseableResources WORLD = new CloseableResources(RenderSystem::assertOnRenderThread,
            () -> Minecraft.getInstance().level != null,
            exception -> MiXianTu.LOGGER.error("Failed to close a client render resource", exception));

    private ClientRenderResources() {
    }

    public static <T extends AutoCloseable> CloseableResources.Slot<T> world(Supplier<T> factory) {
        return WORLD.register(factory);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) WORLD.release();
    }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        WORLD.close();
    }
}
