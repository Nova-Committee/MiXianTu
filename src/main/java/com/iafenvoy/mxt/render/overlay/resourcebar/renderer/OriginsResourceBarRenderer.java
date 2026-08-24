package com.iafenvoy.mxt.render.overlay.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer;
import net.minecraft.client.renderer.RenderPipelines;

public final class OriginsResourceBarRenderer extends ResourceBarRenderer<OriginsRenderData> {

    @Override
    public void render(OriginsRenderData data, Context context) {
        float fill = (float) (data.inverted() ? 1.0D - context.state().percentage() : context.state().percentage());
        context.graphics().blit(RenderPipelines.GUI_TEXTURED, data.texture(), context.x(), context.y(), 0.0F, 0.0F, 71, 5, 256, 256);
        int filled = (int) (fill * 71);
        if (filled > 0)
            context.graphics().blit(RenderPipelines.GUI_TEXTURED, data.texture(), context.x(), context.y() - 2,
                    0.0F, 8 + data.barIndex() * 10, filled, 8, 256, 256);
        ResourceBarRendererHelper.decorations(context, true);
    }
}
