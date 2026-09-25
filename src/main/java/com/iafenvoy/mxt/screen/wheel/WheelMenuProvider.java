package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.api.WheelSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where one source's wheel entries come from: the framework draws a wheel and pages it, it does not decide what
 * is on it. Implementations must be cheap to call once per frame and answer the same thing for the same moment.
 */
@FunctionalInterface
public interface WheelMenuProvider {
    // The entries one source contributes, in fill order: shorter than a page is fine and null is an empty cell.
    // No upper bound - a source with more entries than a page fits takes another page.
    List<@Nullable WheelMenuEntry> entries(@Nullable Player player, WheelSource source);
}