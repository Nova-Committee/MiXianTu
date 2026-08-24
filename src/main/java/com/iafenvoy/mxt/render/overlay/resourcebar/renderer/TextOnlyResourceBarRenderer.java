package com.iafenvoy.mxt.render.overlay.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TextOnlyRenderData;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer;

public final class TextOnlyResourceBarRenderer extends ResourceBarRenderer<TextOnlyRenderData> {

    @Override
    public void render(TextOnlyRenderData data, Context context) {
        ResourceBarRendererHelper.value(context, 0xFF000000 | data.color(), data.showMaximum(), data.format());
        ResourceBarRendererHelper.decorations(context, false);
    }
}
