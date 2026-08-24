package com.iafenvoy.mxt.render.overlay.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Stateless client renderer for one resource-bar render-data type.
 */
public abstract class ResourceBarRenderer<T extends ResourceBarRenderData> {
    public abstract void render(T data, Context context);

    @SuppressWarnings("unchecked")
    public final void renderUnchecked(ResourceBarRenderData data, Context context) {
        this.render((T) data, context);
    }

    public record Context(GuiGraphicsExtractor graphics, Minecraft minecraft, ResourceBarRenderState state, int x,
                          int y) {
    }
}
