package com.iafenvoy.mxt.screen.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.screen.resourcebar.ResourceBarRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class OriginsResourceBarRenderer extends ResourceBarRenderer<OriginsRenderData> {
    private static final int BAR_WIDTH = 71;
    private static final int BAR_HEIGHT = 5;
    // The fill row is 8 tall and starts one row pitch above the bar's own line; the icon column sits past it.
    private static final int ROW_PITCH = 10;
    private static final int FILL_HEIGHT = 8;
    private static final float ICON_COLUMN = 73.0F;

    @Override
    public void render(OriginsRenderData data, Context context) {
        float fill = (float) (data.inverted() ? 1.0D - context.state().percentage() : context.state().percentage());
        IconRenderer.renderSheet(context.graphics(), data.sheet(), context.x(), context.y(), 0.0F, 0.0F,
                BAR_WIDTH, BAR_HEIGHT);
        int filled = (int) (fill * BAR_WIDTH);
        if (filled > 0)
            IconRenderer.renderSheet(context.graphics(), data.sheet(), context.x(), context.y() - 2, 0.0F,
                    FILL_HEIGHT + data.barIndex() * ROW_PITCH, filled, FILL_HEIGHT);
        ResourceBarRendererHelper.decorations(context, true);
    }

    // Where this layout keeps the icon it puts beside the bar, for the shared decoration pass.
    public static void renderIconAt(OriginsRenderData data, GuiGraphicsExtractor graphics, int x, int y) {
        IconRenderer.renderSheet(graphics, data.sheet(), x, y, ICON_COLUMN,
                FILL_HEIGHT + data.resolvedIconIndex() * ROW_PITCH, 8, 8);
    }
}
