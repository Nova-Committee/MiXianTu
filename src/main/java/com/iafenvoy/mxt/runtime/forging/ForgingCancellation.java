package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * What a cancelled forging session does with the materials it locked away; the only answer today is that
 * everything the session took goes back.
 */
@FunctionalInterface
public interface ForgingCancellation {
    /**
     * @param player    who asked to cancel, and who any spilled overflow is given to
     * @param surface   the table; its container is where the materials came from
     * @param state     the session being cancelled; {@link ForgingTableState#consumed()} is what it took
     * @param blueprint the session's blueprint resolved live, or null if the datapack no longer has it -
     *                  for policies that want to be per-blueprint rather than global
     */
    void settle(ServerPlayer player, ForgingSurface surface, ForgingTableState state, @Nullable ForgingBlueprint blueprint);
}
