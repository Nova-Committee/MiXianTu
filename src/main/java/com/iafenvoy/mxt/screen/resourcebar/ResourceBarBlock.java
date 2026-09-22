package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.screen.hud.RenderBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One resource bar offered to the layout.
 */
public record ResourceBarBlock(ResourceBarRenderState state) implements RenderBlock {
    // Body height of mxt:textures/gui/resource_bar.png: a bar occupies texture rows 0..4 of its cell, and the
    // cell below starts ten rows down. Measured from the texture rather than guessed.
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
