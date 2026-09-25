package com.iafenvoy.mxt.screen.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TexturedRenderData;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.screen.resourcebar.ResourceBarRenderer;

public final class TexturedResourceBarRenderer extends ResourceBarRenderer<TexturedRenderData> {

    @Override
    public void render(TexturedRenderData data, Context context) {
        IconRenderer.renderSprite(context.graphics(), data.backgroundSprite(), context.x(), context.y(),
                data.width(), data.height());
        int filled = (int) Math.round(data.width() * context.state().percentage());
        if (filled > 0)
            IconRenderer.renderFill(context.graphics(), data.fillSprite(), context.x(), context.y(),
                    filled, data.height());
        if (data.showValue()) ResourceBarRendererHelper.value(context, 0xFFFFFFFF, true, "%current%");
        ResourceBarRendererHelper.decorations(context, true);
    }
}
