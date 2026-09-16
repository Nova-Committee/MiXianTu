package com.iafenvoy.mxt.testmod;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Prints the window size, GUI scale and screen frame/slot positions, which a screenshot cannot settle:
 * a layout offset and a wrong coordinate space look identical in a picture. Dev-only, and inert unless
 * {@code mxt.clientProbe} names it; a command rather than a tick counter, which would race world load.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ForgingScreenProbe {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ForgingScreenProbe() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        if (!enabled()) return;
        LiteralArgumentBuilder<CommandSourceStack> command =
                Commands.literal("mxtforging").executes(context -> {
                    report();
                    return 1;
                });
        event.getDispatcher().register(command);
    }

    private static boolean enabled() {
        return "forging".equals(System.getProperty("mxt.clientProbe", ""));
    }

    private static void report() {
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        StringBuilder out = new StringBuilder();
        out.append("window=").append(window.getWidth()).append('x').append(window.getHeight())
                .append(" guiScale=").append(window.getGuiScale())
                .append(" guiScaled=").append(window.getGuiScaledWidth()).append('x').append(window.getGuiScaledHeight());
        if (client.screen instanceof AbstractContainerScreen<?> screen) {
            out.append(" frame=").append(screen.getImageWidth()).append('x').append(screen.getImageHeight())
                    .append(" leftPos=").append(screen.getLeftPos())
                    .append(" topPos=").append(screen.getTopPos());
            for (Slot slot : screen.getMenu().slots) {
                out.append(String.format(Locale.ROOT, " [%d]=%d,%d", slot.index, slot.x, slot.y));
            }
        } else {
            out.append(" no container screen open");
        }
        LOGGER.info("MXT-PROBE {}", out);
        if (client.player != null)
            client.player.sendSystemMessage(Component.literal(out.toString()).withStyle(ChatFormatting.AQUA));
    }
}
