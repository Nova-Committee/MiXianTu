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
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Locale;

/**
 * What a run looks like on a client: the two things a tribulation asks of the screen, read from the run itself
 * rather than sent as instructions.
 *
 * <p>{@link #shouldDarken()} is the answer vanilla's darkening decision is given by the boss-overlay mixin, and
 * the wind-up countdown is the action bar's overlay message, refreshed every tick. Both are answered by the same
 * question - is a run under way on a player I can see - which is only possible because the tribulation attachment
 * is already synced to every client that can see the player it belongs to, that player's own client included.
 * The server keeps running the tribulation and this class only watches it, so nothing has to be sent, no boss bar
 * has to exist, and a run that clears stops the effects by itself.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TribulationClientEffects {
    /**
     * How far a run still reaches, in blocks. Ten chunks is vanilla's own answer for the wither, whose client
     * tracking range is ten chunks, so a boss fight and a tribulation reach about equally far.
     */
    private static final double RADIUS = 160.0D;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;
    /**
     * Whether the countdown is on the action bar right now, so it is cleared once instead of every tick.
     */
    private static boolean countdownShown;

    private TribulationClientEffects() {
    }

    /**
     * Whether the world should darken: vanilla's rule is that a boss bar asked for it, and a tribulation asks
     * through the definition its run belongs to. The wind-up darkens like the rest of the run.
     */
    public static boolean shouldDarken() {
        TribulationAttachment run = activeRun();
        return run != null && run.tribulation().map(holder -> holder.value().darkenSky()).orElse(false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        TribulationAttachment run = activeRun();
        long windup = run == null ? 0L : run.windup();
        if (windup > 0L) {
            minecraft.gui.setOverlayMessage(Component.translatable("overlay.mxt.tribulation.windup", seconds(windup)), false);
            countdownShown = true;
        } else if (countdownShown) {
            countdownShown = false;
            minecraft.gui.setOverlayMessage(null, false);
        }
    }

    /**
     * The run the player is closest to being part of: the first player within reach that has one, their own
     * client included, or null when the sky belongs to nobody.
     */
    private static TribulationAttachment activeRun() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return null;
        Player self = minecraft.player;
        for (Player player : level.players()) {
            if (self != null && player != self && player.distanceToSqr(self) > RADIUS_SQUARED) continue;
            TribulationAttachment attachment = player.getExistingData(MxtAttachments.TRIBULATION).orElse(null);
            if (attachment != null && attachment.tribulation().isPresent()) return attachment;
        }
        return null;
    }

    /**
     * The countdown in the unit a player reads it in. The remaining ticks are the whole number here: the value is
     * what the run will really wait out, so no estimate is being made.
     */
    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
