package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wheel's contents as the framework sees them: the registered {@link WheelMenuProvider}, padded so that
 * "one entry per sector" is an invariant. With no provider - or no player - the wheel is empty.
 */
public final class WheelMenuContent {
    /** Taken from the layout that stores the slots, so the two cannot disagree. */
    public static final int SECTORS = WheelLayout.SLOTS;
    private static WheelMenuProvider provider = player -> List.of();

    private WheelMenuContent() {
    }

    /** The last registration wins; {@code null} resets to the empty provider. */
    public static void register(@Nullable WheelMenuProvider content) {
        provider = content == null ? player -> List.of() : content;
    }

    /** The sectors in order, {@code null} for empty ones, always exactly {@link #SECTORS} long. */
    public static List<@Nullable WheelMenuEntry> entries(@Nullable Player player) {
        List<WheelMenuEntry> provided = provider.entries(player);
        List<WheelMenuEntry> sectors = new ArrayList<>(SECTORS);
        for (int sector = 0; sector < SECTORS; sector++)
            sectors.add(sector < provided.size() ? provided.get(sector) : null);
        return Collections.unmodifiableList(sectors);
    }

    public static @Nullable WheelMenuEntry entry(@Nullable Player player, int sector) {
        if (sector < 0 || sector >= SECTORS) return null;
        return entries(player).get(sector);
    }

    /** Whether there is anything to show at all; an empty wheel is never opened. */
    public static boolean isEmpty(@Nullable Player player) {
        for (WheelMenuEntry entry : entries(player)) if (entry != null) return false;
        return true;
    }
}
