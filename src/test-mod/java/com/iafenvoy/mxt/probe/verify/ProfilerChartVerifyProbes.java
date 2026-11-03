package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.profiling.ProfilerChart;
import com.iafenvoy.saturn.profiling.Profiler;
import com.iafenvoy.saturn.profiling.ProfilerSource;
import com.iafenvoy.saturn.profiling.ProfilerWindowSnapshot;
import com.iafenvoy.saturn.registry.SaturnProfilerSources;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ProfilerChartVerifyProbes {
    private ProfilerChartVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/profiling/gui_redraw", ProfilerChartVerifyProbes::redraw));
    }

    private static void redraw(ProbeContext context) {
        BooleanList complete = flags(true, Profiler.WINDOW_TICKS);
        complete.set(12, false);
        ProfilerWindowSnapshot peak = window(rows(Profiler.WINDOW_TICKS, 4_000_000L, 1L), complete);
        complete.set(12, true);
        ProfilerWindowSnapshot idle = window(rows(Profiler.WINDOW_TICKS, 0L, 0L), complete);
        BufferedImage reused = new BufferedImage(Profiler.WINDOW_TICKS, 32, BufferedImage.TYPE_INT_ARGB);
        draw(reused, peak);
        expect(context, "the old row contains a full-height peak", reused.getRGB(20, 5), 0xFF55FF55);
        expect(context, "the old row contains an incomplete-tick marker", reused.getRGB(12, 0), 0xFFFF55FF);
        draw(reused, idle);
        BufferedImage fresh = new BufferedImage(Profiler.WINDOW_TICKS, 32, BufferedImage.TYPE_INT_ARGB);
        draw(fresh, idle);
        expect(context, "a reused row equals a fresh chart in every pixel", Arrays.equals(pixels(reused), pixels(fresh)), true);
        expect(context, "the old peak is immediately replaced", reused.getRGB(20, 5), 0xB0101010);
        expect(context, "the old incomplete-tick marker is immediately replaced", reused.getRGB(12, 0), 0xB0101010);
        expect(context, "the vertical axis has no antialiased edge", reused.getRGB(0, 5), 0xFF707070);
        for (int frame = 0; frame < 8; frame++) draw(reused, idle);
        expect(context, "repeated frames do not accumulate opacity", Arrays.equals(pixels(reused), pixels(fresh)), true);
        draw(reused, peak);
        ProfilerWindowSnapshot partial = window(List.of(new LongArrayList(new long[]{1_000_000L, 1L})), new BooleanArrayList(new boolean[]{true}));
        draw(reused, partial);
        draw(fresh, partial);
        expect(context, "a shorter window clears unused history", Arrays.equals(pixels(reused), pixels(fresh)), true);
    }

    private static List<LongList> rows(int count, long... row) {
        List<LongList> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) rows.add(new LongArrayList(row));
        return rows;
    }

    private static BooleanList flags(boolean value, int count) {
        BooleanList flags = new BooleanArrayList(count);
        for (int index = 0; index < count; index++) flags.add(value);
        return flags;
    }

    private static ProfilerWindowSnapshot window(List<LongList> samples, BooleanList complete) {
        ProfilerSource source = SaturnProfilerSources.COST;
        List<ProfilerSource> sources = List.of(source);
        return new ProfilerWindowSnapshot(1L, 1L, sources,
                List.of(new ProfilerWindowSnapshot.Producer(1L, "Server thread", 0L, sources, samples, complete, 0L)), 0L);
    }

    private static void draw(BufferedImage image, ProfilerWindowSnapshot window) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ProfilerChart.draw(graphics, window.producers().getFirst(), window.sources().getFirst(), 4D);
        } finally {
            graphics.dispose();
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static void expect(ProbeContext context, String message, Object actual, Object expected) {
        context.expect(message, actual, expected);
        context.note(message + ": " + (Objects.equals(actual, expected) ? "OK" : "MISMATCH")
                + " actual=" + actual + " expected=" + expected);
    }
}
