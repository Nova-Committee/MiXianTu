package com.iafenvoy.mxt.compat.ponder;

import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

// Ponder 1.0.52 queues tooltips at (0, 0) after translating a pose that deferred rendering no longer uses.
// Remove this bridge once upstream passes absolute GUI coordinates to the deferred tooltip API.
public final class PonderTooltipCompatibility {
    private PonderTooltipCompatibility() {
    }

    public static void reposition(RenderTooltipEvent.Pre event, int mouseX, int mouseY) {
        if (event.getX() != 0 || event.getY() != 0
                || event.getTooltipPositioner() != DefaultTooltipPositioner.INSTANCE) return;
        event.setX(mouseX);
        event.setY(mouseY);
    }
}
