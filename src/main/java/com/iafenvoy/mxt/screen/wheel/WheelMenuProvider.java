package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where one source's wheel entries come from: the framework draws a wheel and pages it, it does not decide what
 * is on it. Implementations must be cheap to call once per frame and answer the same thing for the same moment.
 */
@FunctionalInterface
public interface WheelMenuProvider {
    /**
     * The entries one source contributes, in the order its cells are filled: shorter than a page is fine and a
     * {@code null} element is an empty cell.
     *
     * <p>There is no upper bound. A source that holds more entries than one page fits takes another page, so
     * giving the player fifteen abilities gives them four rows of cells, not twelve and three lost ones.</p>
     */
    List<@Nullable WheelMenuEntry> entries(@Nullable Player player, WheelSource source);
}
