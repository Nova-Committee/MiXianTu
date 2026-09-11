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
 * Prints the numbers a screenshot cannot be read reliably for.
 *
 * <p>Whether a drawing is offset because the layout is wrong or because it is written in the wrong
 * coordinate space is not answerable from a picture: the two look identical. The window size, the
 * GUI scale, and where the screen thinks its own frame and slots are do answer it, so this command
 * prints them next to the constants the screen draws with.
 *
 * <p>Dev-only, and inert unless {@code mxt.clientProbe} names it. A command rather than a tick
 * counter on purpose: a tick-based probe races world load, and a command runs exactly when asked.
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
