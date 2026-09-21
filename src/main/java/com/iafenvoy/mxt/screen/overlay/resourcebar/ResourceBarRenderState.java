package com.iafenvoy.mxt.screen.overlay.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Per-frame presentation state for one resource bar, including the slot of the column it belongs to.
 *
 * <p>{@code layoutX} / {@code layoutY} are the bar's position <em>within its column</em>, computed from the
 * bars above it and the column's own width. The column's placement is added on top of that by
 * {@link ResourceBarRenderer.Context}, which is the one place that knows where the column was dragged
 * to.</p>
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

    /**
     * A copy of this bar placed at a slot of the column, which is how the layout is applied without every
     * producer having to know how tall the bars above it turned out to be.
     */
    public ResourceBarRenderState at(int x, int y) {
        return new ResourceBarRenderState(this.context, this.anchor, this.order, this.id, this.index,
                this.current, this.minimum, this.maximum, this.renderData, x, y, this.name, this.valueDisplay);
    }

    /**
     * This bar's slot offered to the layout.
     */
    public ResourceBarBlock block() {
        return new ResourceBarBlock(this);
    }
}
