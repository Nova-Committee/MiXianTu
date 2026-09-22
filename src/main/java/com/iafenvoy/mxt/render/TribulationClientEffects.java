package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

import java.util.Locale;

/**
 * What a tribulation asks of the screen: the sky darkening and the wind-up countdown, both read from the
 * tribulation attachment that is already synced to every client that can see its player. The server keeps running
 * the run; this class only watches it, so nothing has to be sent and a cleared run stops the effects by itself.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TribulationClientEffects {
    // 160 blocks: ten chunks is vanilla's own client tracking range for the wither, so a fight and a run reach alike.
    private static final double RADIUS = 160.0D;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;
    // Whether the countdown is on the action bar right now, so it is cleared once instead of every tick.
    private static boolean countdownShown;

    private TribulationClientEffects() {
    }

    // Vanilla only darkens the sky when a boss bar asked for it; the boss-overlay mixin asks this instead.
    public static boolean shouldDarken() {
        TribulationAttachment run = activeRun();
        return run != null && run.tribulation().map(holder -> holder.value().darkenSky()).orElse(false);
    }

    @SubscribeEvent
    public static void onClientTick(Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        TribulationAttachment run = activeRun();
        long windup = run == null ? 0L : run.windup();
        if (windup > 0L) {
            minecraft.gui.setOverlayMessage(Component.translatable("overlay.mxt.tribulation.windup", seconds(windup)), false);
            countdownShown = true;
        } else if (countdownShown) {
            countdownShown = false;
            minecraft.gui.setOverlayMessage(Component.empty(), false);
        }
    }

    private static TribulationAttachment activeRun() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return null;
        Player self = minecraft.player;
        // Only other players are distance-filtered: the player's own run counts wherever they are.
        for (Player player : level.players()) {
            if (self != null && player != self && player.distanceToSqr(self) > RADIUS_SQUARED) continue;
            TribulationAttachment attachment = player.getExistingData(MxtAttachments.TRIBULATION).orElse(null);
            if (attachment != null && attachment.tribulation().isPresent()) return attachment;
        }
        return null;
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
