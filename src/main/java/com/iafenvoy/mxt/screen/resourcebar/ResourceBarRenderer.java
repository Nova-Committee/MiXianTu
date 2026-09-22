package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Stateless client renderer for one resource-bar render-data type. Every coordinate it draws at is supplied by
 * the layout, so it knows what a bar looks like and nothing about where it goes.
 */
public abstract class ResourceBarRenderer<T extends ResourceBarRenderData> {
    public abstract void render(T data, Context context);

    @SuppressWarnings("unchecked")
    public final void renderUnchecked(ResourceBarRenderData data, Context context) {
        this.render((T) data, context);
    }

    // Both coordinates are absolute screen pixels, so a renderer never has to know where the column holding it
    // was placed.
    public record Context(GuiGraphicsExtractor graphics, Minecraft minecraft, ResourceBarRenderState state, int x,
                          int y) {
    }
}
