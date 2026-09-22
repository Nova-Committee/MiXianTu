package com.iafenvoy.mxt.screen.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The one place a laid-out HUD element is drawn: a module hands over {@link RenderBlock}s carrying sizes and
 * nothing else, and this stacks them inside the element's rectangle. No module ever computes a screen
 * coordinate.
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    // The only place a block's screen position is computed: the cursor starts at the stack's top and each
    // block advances it by the height that block reports, so what is drawn matches RenderBlock#boundsOf.
    public static void renderColumn(GuiGraphicsExtractor graphics, List<RenderBlock> blocks, int left, int top) {
        if (blocks.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int running = 0;
        for (RenderBlock block : blocks) {
            block.draw(graphics, minecraft, left + block.left(), top + running);
            running += block.height();
        }
    }

    public static void renderStanding(GuiGraphicsExtractor graphics, List<RenderBlock> blocks, int left, int bottom) {
        if (blocks.isEmpty()) return;
        renderColumn(graphics, blocks, left, bottom - RenderBlock.boundsOf(blocks).height());
    }
}
