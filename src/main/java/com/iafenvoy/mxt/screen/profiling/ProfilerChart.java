package com.iafenvoy.mxt.screen.profiling;

import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.saturn.profiling.Profiler;
import com.iafenvoy.saturn.profiling.ProfilerSource;
import com.iafenvoy.saturn.profiling.ProfilerWindowSnapshot;

import java.awt.*;

public final class ProfilerChart {
    private ProfilerChart() {
    }

    public static void draw(Graphics2D graphics, ProfilerWindowSnapshot.Producer producer, ProfilerSource source, double ceiling) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Replace every pixel so sorting cannot retain the previous row's chart.
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(new Color(AuiStyles.SURFACE, true));
        graphics.fillRect(0, 0, Profiler.WINDOW_TICKS, 32);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(AuiStyles.INK_MUTED, true));
        graphics.drawLine(0, 0, 0, 31);
        graphics.drawLine(0, 31, Profiler.WINDOW_TICKS - 1, 31);
        int offset = Profiler.WINDOW_TICKS - producer.sampleCount();
        for (int tick = 0; tick < producer.sampleCount(); tick++) {
            if (!producer.complete(tick)) {
                graphics.setColor(new Color(AuiStyles.PURPLE, true));
                graphics.fillRect(offset + tick, 0, 1, 2);
                continue;
            }
            long nanos = producer.nanos(tick, source);
            int height = (int) Math.min(31D, Math.ceil(nanos / (ceiling * 1_000_000D) * 31D));
            graphics.setColor(new Color(nanos >= 50_000_000L ? AuiStyles.RED : nanos >= 10_000_000L ? AuiStyles.GOLD : AuiStyles.GREEN, true));
            if (height > 0) graphics.fillRect(offset + tick, 31 - height, 1, height);
        }
    }
}
