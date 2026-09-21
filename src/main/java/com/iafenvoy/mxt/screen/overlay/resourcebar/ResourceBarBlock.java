package com.iafenvoy.mxt.screen.overlay.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.screen.overlay.hud.RenderBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One resource bar offered to the layout.
 *
 * <p>It carries nothing but its own state and its slot in the column; where that lands on screen is the
 * layout's business. The slot is reported as {@link #left()} / {@link #top()} rather than being added to the
 * coordinates here, which is the same convention every other block follows: a block says how far into its
 * stack it sits, never where the stack is.</p>
 *
 * <h2>Height</h2>
 * <p>{@link #height()} is the room the bar takes in the stack, and that is the five pixels an Origins-style
 * bar's texture actually paints - not the eight its render data declares. The difference is not cosmetic:
 * the stacking that was already here advances by this number, so any overstatement shows up as a gap between
 * every pair of bars, which is exactly what the declared eight produced. The declared height is still there
 * for callers that want the render data's own size.</p>
 */
public record ResourceBarBlock(ResourceBarRenderState state) implements RenderBlock {
    /**
     * The body height of {@code mxt:textures/gui/resource_bar.png}: a bar occupies texture rows {@code 0..4}
     * of its cell, and the cell below starts ten rows further down. Measured from the texture rather than
     * guessed, because the renderer's own numbers - a 71x5 body plus an 71x8 fill starting two pixels up -
     * only make sense next to the image they were written against.
     */
    private static final int ORIGINS_HEIGHT = 5;

    @Override
    public void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y) {
        ResourceBarRendererDispatcher.render(new ResourceBarRenderer.Context(
                graphics, minecraft, this.state, x, y));
    }

    @Override
    public int width() {
        return this.state.renderData().width();
    }

    @Override
    public int height() {
        return this.state.renderData() instanceof OriginsRenderData
                ? ORIGINS_HEIGHT
                : this.state.renderData().height();
    }

    @Override
    public int left() {
        return this.state.layoutX();
    }
}
