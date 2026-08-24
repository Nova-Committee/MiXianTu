package com.iafenvoy.mxt.render.overlay.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TexturedRenderData;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer;
import net.minecraft.client.renderer.RenderPipelines;

public final class TexturedResourceBarRenderer extends ResourceBarRenderer<TexturedRenderData> {

    @Override
    public void render(TexturedRenderData data, Context context) {
        context.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, data.backgroundSprite(), context.x(), context.y(), data.width(), data.height());
        int filled = (int) Math.round(data.width() * context.state().percentage());
        if (filled > 0)
            context.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, data.fillSprite(), context.x(), context.y(), filled, data.height());
        if (data.showValue()) ResourceBarRendererHelper.value(context, 0xFFFFFFFF, true, "%current%");
        ResourceBarRendererHelper.decorations(context, true);
    }
}
