package com.iafenvoy.mxt.render.overlay.resourcebar;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.MissingRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.RadialRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.SegmentedRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TextOnlyRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TexturedRenderData;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.render.overlay.resourcebar.renderer.MissingResourceBarRenderer;
import com.iafenvoy.mxt.render.overlay.resourcebar.renderer.OriginsResourceBarRenderer;
import com.iafenvoy.mxt.render.overlay.resourcebar.renderer.RadialResourceBarRenderer;
import com.iafenvoy.mxt.render.overlay.resourcebar.renderer.SegmentedResourceBarRenderer;
import com.iafenvoy.mxt.render.overlay.resourcebar.renderer.TextOnlyResourceBarRenderer;
import com.iafenvoy.mxt.render.overlay.resourcebar.renderer.TexturedResourceBarRenderer;
import com.mojang.serialization.MapCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side dispatch from a render-data codec to its shared renderer instance.
 */
public final class ResourceBarRendererDispatcher {
    public static final Map<MapCodec<? extends ResourceBarRenderData>, ResourceBarRenderer<?>> RENDERERS = new HashMap<>();

    static {
        register(TexturedRenderData.CODEC, new TexturedResourceBarRenderer());
        register(OriginsRenderData.CODEC, new OriginsResourceBarRenderer());
        register(SegmentedRenderData.CODEC, new SegmentedResourceBarRenderer());
        register(RadialRenderData.CODEC, new RadialResourceBarRenderer());
        register(TextOnlyRenderData.CODEC, new TextOnlyResourceBarRenderer());
        register(MissingRenderData.CODEC, new MissingResourceBarRenderer());
    }

    public static <T extends ResourceBarRenderData> void register(MapCodec<T> codec, ResourceBarRenderer<T> renderer) {
        RENDERERS.put(codec, renderer);
    }

    public static void render(ResourceBarRenderer.Context context) {
        ResourceBarRenderData data = context.state().renderData();
        ResourceBarRenderer<?> renderer = RENDERERS.get(data.codec());
        if (renderer == null)
            throw new IllegalStateException("Renderer " + MxtRegistries.RESOURCE_BAR_RENDER_DATA_TYPE.getKey(data.codec())
                    + " has no registered client renderer");
        renderer.renderUnchecked(data, context);
    }
}
