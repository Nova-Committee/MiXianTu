package com.iafenvoy.mxt.render.overlay.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.Context;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Per-frame presentation state for one resource bar.
 */
public record ResourceBarRenderState(Context context, Anchor anchor, int order, Identifier id, int index,
                                     double current, double minimum, double maximum, ResourceBarRenderData renderData,
                                     Optional<Component> name, ValueDisplay valueDisplay) {
    public double percentage() {
        if (!Double.isFinite(this.current) || !Double.isFinite(this.minimum) || !Double.isFinite(this.maximum))
            return 0.0D;
        return this.maximum == this.minimum ? 1.0D
                : Math.clamp((this.current - this.minimum) / (this.maximum - this.minimum), 0.0D, 1.0D);
    }
}
