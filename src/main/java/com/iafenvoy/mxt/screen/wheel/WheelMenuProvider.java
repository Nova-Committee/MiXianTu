package com.iafenvoy.mxt.screen.wheel;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where the wheel's twelve sectors come from: the framework draws a wheel, it does not decide what is on it.
 * Implementations must be cheap to call once per frame and answer the same thing for the same moment.
 */
@FunctionalInterface
public interface WheelMenuProvider {
    /** Sectors from 0 (straight up) clockwise; shorter is fine and a {@code null} element is an empty sector. */
    List<@Nullable WheelMenuEntry> entries(@Nullable Player player);
}
