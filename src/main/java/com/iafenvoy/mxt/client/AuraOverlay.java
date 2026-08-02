package com.iafenvoy.mxt.client;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal client view of the synchronized current-chunk aura attachment.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public enum AuraOverlay implements GuiLayer {
    INSTANCE;
    private static final int WIDTH = 80;

    @Override
    public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null) return;
        AuraChunkData aura = minecraft.level.getChunkAt(player.blockPosition()).getData(MxtAttachments.AURA_CHUNK);
        if (!aura.initialized()) return;
        int x = minecraft.getWindow().getGuiScaledWidth() / 2 - WIDTH / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 61;
        int fill = (int) Math.clamp(Math.round(aura.concentration()), 0L, WIDTH);
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + 7, 0xB0000000);
        graphics.fill(x, y, x + WIDTH, y + 5, 0xFF26313F);
        graphics.fill(x, y, x + fill, y + 5, 0xFF57D7E3);
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura"), INSTANCE);
    }
}
