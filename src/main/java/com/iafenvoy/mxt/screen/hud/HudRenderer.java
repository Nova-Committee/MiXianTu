package com.iafenvoy.mxt.screen.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The one place a laid-out HUD element is drawn.
 *
 * <p>Everything visible about a HUD element passes through here. A module describes what it wants to show -
 * a list of {@link RenderBlock}s with sizes and nothing else - and this class decides where the stack lands:
 * stacked top to bottom inside the element's rectangle, whose corner comes from the element's
 * {@link HudAnchor}. That is what makes the layout responsible for drawing rather than the modules that
 * supply the content.</p>
 *
 * <p>The block list is what carries the geometry, so a block drawn at an offset from its stack is still
 * placed by the layout: the entry says "this one sits this far in", and this class adds it to the stack's
 * position. No module ever computes a screen coordinate.</p>
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    /**
     * Draws a vertical stack of blocks with the stack's top-left corner at {@code left} / {@code top}.
     *
     * <p>This is the only place a block's screen position is computed: the cursor starts at the stack's top
     * and each block advances it by the height that block reports. A block therefore never needs to know
     * where in the stack it sits, and the room the stack occupies - {@link RenderBlock#boundsOf} - is by
     * construction the same number the drawing walks through.</p>
     */
    public static void renderColumn(GuiGraphicsExtractor graphics, List<RenderBlock> blocks, int left, int top) {
        if (blocks.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int running = 0;
        for (RenderBlock block : blocks) {
            block.draw(graphics, minecraft, left + block.left(), top + running);
            running += block.height();
        }
    }

    /**
     * Draws a stack of blocks anchored by its last block's bottom edge, so that {@code bottom} is where the
     * stack ends and everything grows upward from it.
     */
    public static void renderStanding(GuiGraphicsExtractor graphics, List<RenderBlock> blocks, int left, int bottom) {
        if (blocks.isEmpty()) return;
        renderColumn(graphics, blocks, left, bottom - RenderBlock.boundsOf(blocks).height());
    }
}
