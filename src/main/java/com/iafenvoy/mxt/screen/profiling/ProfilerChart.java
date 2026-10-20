package com.iafenvoy.mxt.screen.profiling;

import com.iafenvoy.mxt.util.profiling.Profiler;
import com.iafenvoy.mxt.util.profiling.ProfilerSource;
import com.iafenvoy.mxt.util.profiling.ProfilerWindowSnapshot;

import java.awt.*;

public final class ProfilerChart {
    private ProfilerChart() {
    }

    public static void draw(Graphics2D graphics, ProfilerWindowSnapshot.Producer producer, ProfilerSource source, double ceiling) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Replace the translucent background instead of blending it with the previous chart.
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(new Color(0xB0101010, true));
        graphics.fillRect(0, 0, Profiler.WINDOW_TICKS, 32);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(0xFF707070, true));
        graphics.drawLine(0, 0, 0, 31);
        graphics.drawLine(0, 31, Profiler.WINDOW_TICKS - 1, 31);
        int offset = Profiler.WINDOW_TICKS - producer.sampleCount();
        for (int tick = 0; tick < producer.sampleCount(); tick++) {
            if (!producer.complete(tick)) {
                graphics.setColor(new Color(0xFFFF55FF, true));
                graphics.fillRect(offset + tick, 0, 1, 2);
                continue;
            }
            long nanos = producer.nanos(tick, source);
            int height = (int) Math.min(31D, Math.ceil(nanos / (ceiling * 1_000_000D) * 31D));
            graphics.setColor(new Color(nanos >= 50_000_000L ? 0xFFFF5555 : nanos >= 10_000_000L ? 0xFFFFFF55 : 0xFF55FF55, true));
            if (height > 0) graphics.fillRect(offset + tick, 31 - height, 1, height);
        }
    }
}
