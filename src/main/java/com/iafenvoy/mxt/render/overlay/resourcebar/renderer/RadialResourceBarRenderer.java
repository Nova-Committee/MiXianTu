package com.iafenvoy.mxt.render.overlay.resourcebar.renderer;

import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.RadialRenderData;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer;

public final class RadialResourceBarRenderer extends ResourceBarRenderer<RadialRenderData> {

    @Override
    public void render(RadialRenderData data, Context context) {
        int centerX = context.x() + data.radius();
        int centerY = context.y() + data.radius();
        int half = Math.max(1, data.thickness() / 2);
        drawArc(context, data, centerX, centerY, 1.0D, 0xFF243047, half);
        drawArc(context, data, centerX, centerY, context.state().percentage(), 0xFF000000 | data.fillColor(), half);
        ResourceBarRendererHelper.decorations(context, true);
    }

    private static void drawArc(Context context, RadialRenderData data, int centerX, int centerY,
                                double percentage, int color, int half) {
        double span = data.endAngle() - data.startAngle();
        int steps = Math.max(1, (int) Math.ceil(Math.abs(span) * percentage));
        for (int step = 0; step <= steps; step++) {
            double angle = Math.toRadians(data.startAngle() + span * step / steps * percentage);
            int x = centerX + (int) Math.round(Math.cos(angle) * data.radius());
            int y = centerY + (int) Math.round(Math.sin(angle) * data.radius());
            context.graphics().fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }
}
