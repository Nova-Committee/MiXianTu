package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Per-frame presentation state for one resource bar, including its slot in the column. {@code layoutX} /
 * {@code layoutY} are relative to the column; the column's own placement is added by
 * {@link ResourceBarRenderer.Context}.
 */
public record ResourceBarRenderState(ResourceBarContext context, Anchor anchor, int order, Identifier id, int index,
                                     double current, double minimum, double maximum, ResourceBarRenderData renderData,
                                     int layoutX, int layoutY, Optional<Component> name, ValueDisplay valueDisplay) {
    public double percentage() {
        if (!Double.isFinite(this.current) || !Double.isFinite(this.minimum) || !Double.isFinite(this.maximum))
            return 0.0D;
        return this.maximum == this.minimum ? 1.0D
                : Math.clamp((this.current - this.minimum) / (this.maximum - this.minimum), 0.0D, 1.0D);
    }

    // A copy at a slot of the column, so the layout is applied without every producer knowing how tall the
    // bars above it turned out to be.
    public ResourceBarRenderState at(int x, int y) {
        return new ResourceBarRenderState(this.context, this.anchor, this.order, this.id, this.index,
                this.current, this.minimum, this.maximum, this.renderData, x, y, this.name, this.valueDisplay);
    }

    public ResourceBarBlock block() {
        return new ResourceBarBlock(this);
    }
}
