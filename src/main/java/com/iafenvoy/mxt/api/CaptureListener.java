package com.iafenvoy.mxt.api;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A creature that wants to be told when something captures or releases it. This is not a gate: any creature may
 * be captured, and how a capture works - what it may hold, what it costs, whether it needs a contract at all -
 * is the capturing item's own business. A creature that implements this only hears about the two moments.
 */
public interface CaptureListener {
    // After the capturing item has taken the creature out of the world. The captor is empty when it was no
    // player's doing.
    default void onCaptured(@Nullable Player captor) {
    }

    // After the creature is back in a level and no longer held by whatever carried it.
    default void onReleased(@Nullable Player captor) {
    }
}
