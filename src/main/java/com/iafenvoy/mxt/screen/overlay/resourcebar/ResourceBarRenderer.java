package com.iafenvoy.mxt.screen.overlay.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Stateless client renderer for one resource-bar render-data type.
 *
 * <p>These renderers know what a bar looks like and nothing about where it goes: every coordinate they use is
 * relative to {@code x} / {@code y}, which the layout supplies. Names, icons and values all hang off those two
 * numbers, which is why they follow a column that the player has dragged.</p>
 */
public abstract class ResourceBarRenderer<T extends ResourceBarRenderData> {
    public abstract void render(T data, Context context);

    @SuppressWarnings("unchecked")
    public final void renderUnchecked(ResourceBarRenderData data, Context context) {
        this.render((T) data, context);
    }

    /**
     * The drawing context for one bar, in screen pixels. Both coordinates are absolute, so a renderer never
     * has to know where the column holding it was placed.
     */
    public record Context(GuiGraphicsExtractor graphics, Minecraft minecraft, ResourceBarRenderState state, int x,
                          int y) {
    }
}
