package com.iafenvoy.mxt.screen.wheel;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The ring itself: one sector per slot, as coloured quads. A GUI render state rather than a fill, because a
 * sector is not a rectangle; {@code selected} is the sector that grows, or out of range for none. Each sector is
 * cut into {@link #ARC_SEGMENTS} quads, since a single chord would read as a dodecagon rather than a wheel.
 */
record WheelRingRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                            WheelGeometry.Ring ring, int[] colors, int selected,
                            @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements GuiElementRenderState {
    private static final int ARC_SEGMENTS = 6;

    WheelRingRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                         WheelGeometry.Ring ring, int[] colors, int selected,
                         @Nullable ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, pose, ring, colors, selected, scissorArea, boundsOf(ring, pose, scissorArea));
    }

    @Override
    public void buildVertices(@NonNull VertexConsumer vertexConsumer) {
        double grow = this.ring.grow();
        for (int slot = 0; slot < this.colors.length; slot++) {
            boolean pointed = slot == this.selected;
            double inner = this.ring.innerRadius() - (pointed ? grow : 0.0D);
            double outer = this.ring.outerRadius() + (pointed ? grow : 0.0D);
            int color = this.colors[slot];
            double start = WheelGeometry.sectorStart(slot);
            for (int step = 0; step < ARC_SEGMENTS; step++) {
                double from = start + WheelGeometry.SECTOR_DEGREES * step / ARC_SEGMENTS;
                double to = start + WheelGeometry.SECTOR_DEGREES * (step + 1) / ARC_SEGMENTS;
                this.vertex(vertexConsumer, from, outer, color);
                this.vertex(vertexConsumer, from, inner, color);
                this.vertex(vertexConsumer, to, inner, color);
                this.vertex(vertexConsumer, to, outer, color);
            }
        }
    }

    private void vertex(VertexConsumer vertexConsumer, double angleDegrees, double radius, int color) {
        vertexConsumer.addVertexWith2DPose(this.pose(),
                (float) this.ring.x(angleDegrees, radius), (float) this.ring.y(angleDegrees, radius))
                .setColor(color);
    }

    // The area the ring can touch: the widest radius plus the pop, since any sector may be selected.
    private static @Nullable ScreenRectangle boundsOf(WheelGeometry.Ring ring, Matrix3x2fc pose,
                                                     @Nullable ScreenRectangle scissorArea) {
        double reach = ring.outerRadius() + ring.grow();
        int x0 = (int) Math.floor(ring.centreX() - reach);
        int y0 = (int) Math.floor(ring.centreY() - reach);
        int x1 = (int) Math.ceil(ring.centreX() + reach);
        int y1 = (int) Math.ceil(ring.centreY() + reach);
        ScreenRectangle bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }
}
