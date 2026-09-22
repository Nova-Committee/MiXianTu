package com.iafenvoy.mxt.screen.wheel;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Which sector the player last pointed at, kept after the wheel closes so the use key has a target while no
 * wheel is on screen. Per session and client side: a sector index means nothing without the layout it was
 * chosen against, so {@link ClientPlayerNetworkEvent.LoggingIn} resets it - the entry itself is what the
 * player's attachment remembers.
 *
 * <p>The twelve sectors are resolved here too, because the HUD draws all of them every frame and resolving
 * walks the aura registry and evaluates a formula per aura.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelSelectionState {
    /** No sector chosen in this session yet, which is also what "the use key has no target" means. */
    private static final int NONE = -1;
    private static int sector = NONE;
    /**
     * This tick's wheel; shorter than {@link WheelMenuContent#SECTORS} until the first refresh.
     */
    private static List<@Nullable WheelMenuEntry> sectors = List.of();
    /** The resolved selection, cached because resolving walks the aura registry and the HUD asks every frame. */
    private static @Nullable WheelMenuEntry current;

    private WheelSelectionState() {
    }

    /** The remembered sector, or {@code -1} when nothing has been chosen yet. */
    public static int sector() {
        return sector;
    }

    /** The resolved entry of {@link #sector()}, or {@code null} when nothing is selected. */
    public static @Nullable WheelMenuEntry selected() {
        return current;
    }

    /**
     * All twelve sectors as of the last refresh, {@code null} for an empty one; never modified.
     */
    public static List<@Nullable WheelMenuEntry> sectors() {
        return sectors;
    }

    /** Re-resolves the wheel for a player; called once per client tick, before anything draws or acts on it. */
    public static void refresh(@Nullable Player player) {
        sectors = WheelMenuContent.entries(player);
        int sector = sector();
        current = sector == NONE || sector >= sectors.size() ? null : sectors.get(sector);
    }

    /** Remembers the sector the pointer is on; an out-of-range index is a bug, so it fails loudly. */
    public static void select(int sector) {
        if (sector < 0 || sector >= WheelGeometry.SECTORS)
            throw new IllegalArgumentException("Sector out of range: " + sector);
        WheelSelectionState.sector = sector;
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        sector = NONE;
        sectors = List.of();
        current = null;
    }
}
