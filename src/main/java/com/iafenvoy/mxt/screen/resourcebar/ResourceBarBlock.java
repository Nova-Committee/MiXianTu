package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.screen.hud.RenderBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One resource bar offered to the layout.
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
