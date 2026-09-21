package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.network.payload.WheelSelectionC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
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

import java.util.Optional;

/**
 * Mirrors the armed wheel entry with the server: restores the entry remembered at login and publishes the
 * sector the pointer moves to. The entry is stored rather than the sector number, so a rearranged wheel still
 * comes back armed with the same thing.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelSelectionSync {
    /** No sector: nothing is armed, or the armed sector is empty. */
    private static final int NONE = -1;
    /** Whether this session has read the server's copy; until it has, an unarmed session stays silent. */
    private static boolean consulted;
    /** The sector the server was last told about; {@link #NONE} before anything has been sent. */
    private static int synced = NONE;

    private WheelSelectionSync() {
    }

    /** {@link EventPriority#LOWEST} keeps it after the wheel controller: the sector picked this tick wins. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        if (!consulted) {
            int chosen = WheelSelectionState.sector();
            // Armed before the server's copy arrived: the player's hand wins and nothing is left to restore.
            if (chosen >= 0) consulted = true;
            else {
                consult(player);
                if (!consulted) return;
            }
        }
        int sector = WheelSelectionState.sector();
        if (sector == synced) return;
        synced = sector;
        ClientPacketDistributor.sendToServer(new WheelSelectionC2SPayload(slot(player, sector)));
    }

    /** Armed state does not survive a world change: the attachment is the server's, the wheel may differ. */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        consulted = false;
        synced = NONE;
    }

    /** Re-sends the armed sector, because the wheel it was chosen against was just replaced by the editor. */
    static void republish() {
        synced = NONE;
    }

    private static void consult(Player player) {
        // Arrives after the join: "not here yet" and "never armed" look alike, so it retries each tick.
        WheelLayoutAttachment data = player.getExistingData(MxtAttachments.WHEEL_LAYOUT).orElse(null);
        if (data == null) return;
        consulted = true;
        WheelSlot saved = data.selection().orElse(null);
        // An empty slot names no sector, so nothing ever stores one.
        if (saved == null || saved.isEmpty()) return;
        WheelLayout layout = WheelContent.layoutFor(player);
        int sector = sectorOf(layout, saved);
        if (sector < 0) {
            MiXianTu.LOGGER.warn("Discarding the remembered wheel selection {}: this player's wheel does not hold it", saved);
            clear();
            return;
        }
        // A deleted definition cannot be resolved at all; merely unusable right now is a normal state.
        if (!saved.kind().exists(player.level().registryAccess(), saved.id())) {
            MiXianTu.LOGGER.warn("Discarding the remembered wheel selection {}: no such {} definition",
                    saved, saved.kind().getSerializedName());
            clear();
            return;
        }
        WheelSelectionState.select(sector);
        synced = sector;
    }

    /** Forgets the remembered selection, so the warning is not repeated on the next login. */
    private static void clear() {
        synced = NONE;
        ClientPacketDistributor.sendToServer(new WheelSelectionC2SPayload(Optional.empty()));
    }

    /** The slot a sector holds, or empty when nothing is armed - both cases mean the same to the server. */
    private static Optional<WheelSlot> slot(Player player, int sector) {
        if (sector < 0) return Optional.empty();
        WheelSlot slot = WheelContent.layoutFor(player).slot(sector);
        return slot.isEmpty() ? Optional.empty() : Optional.of(slot);
    }

    /** The sector holding this exact slot, or {@link #NONE} when the wheel has it nowhere. */
    private static int sectorOf(WheelLayout layout, WheelSlot slot) {
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++)
            if (layout.slot(sector).equals(slot)) return sector;
        return NONE;
    }
}
