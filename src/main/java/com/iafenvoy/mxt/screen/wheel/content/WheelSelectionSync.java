package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.network.payload.WheelSelectionC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.screen.wheel.WheelSelectionState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Mirrors the chosen cell with the server: restores the number remembered at login and publishes the one the
 * player moves to. Only the number travels - cells are numbered across the whole wheel, so it stays valid while
 * the pages it was counted on come and go.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelSelectionSync {
    // Whether this session has read the server's copy; until it has, an unarmed session stays silent.
    private static boolean consulted;
    // The number the server was last told about; null before anything has been sent.
    private static @Nullable Integer synced;

    private WheelSelectionSync() {
    }

    // EventPriority#LOWEST keeps it after the wheel controller: the cell picked this tick wins.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        if (!consulted) {
            // Chosen before the server's copy arrived: the player's hand wins and nothing is left to restore.
            if (WheelSelectionState.number() >= 0) consulted = true;
            else {
                consult(player);
                if (!consulted) return;
            }
        }
        Integer armed = WheelSelectionState.number() < 0 ? null : WheelSelectionState.number();
        if (Objects.equals(armed, synced)) return;
        synced = armed;
        ClientPacketDistributor.sendToServer(new WheelSelectionC2SPayload(Optional.ofNullable(armed)));
    }

    // What was chosen does not survive a world change: the attachment is the server's, the pages may differ.
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        consulted = false;
        synced = null;
    }

    // Re-sends the chosen cell, because the pages it was counted on were just replaced by the editor.
    static void republish() {
        synced = null;
    }

    private static void consult(Player player) {
        // Arrives after the join: "not here yet" and "never chose anything" look alike, so it retries each tick.
        WheelLayoutAttachment data = player.getExistingData(MxtAttachments.WHEEL_LAYOUT).orElse(null);
        if (data == null) return;
        consulted = true;
        Integer saved = data.armed().orElse(null);
        if (saved == null) return;
        WheelSelectionState.restore(saved);
        synced = saved;
    }
}
