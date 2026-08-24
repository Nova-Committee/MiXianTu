package com.iafenvoy.mxt.render.overlay.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.MissingRenderData;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer;

public final class MissingResourceBarRenderer extends ResourceBarRenderer<MissingRenderData> {

    @Override
    public void render(MissingRenderData data, Context context) {
        ResourceBarRendererHelper.decorations(context, false);
    }
}
