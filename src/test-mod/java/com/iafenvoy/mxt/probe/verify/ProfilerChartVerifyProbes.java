package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.profiling.ProfilerChart;
import com.iafenvoy.mxt.util.profiling.Profiler;
import com.iafenvoy.mxt.util.profiling.ProfilerWindowSnapshot;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class ProfilerChartVerifyProbes {
    private ProfilerChartVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/profiling/gui_redraw", ProfilerChartVerifyProbes::redraw),
                Probe.of("verify/profiling/gui_sampling", ProfilerChartVerifyProbes::sampling));
    }

    private static void sampling(ProbeContext context) {
        String html = new String(resource("/assets/apricityui/apricity/mxt/profiler/profiler.html"), StandardCharsets.UTF_8);
        List<String> charts = Pattern.compile("<canvas\\b[^>]*>").matcher(html).results().map(MatchResult::group).toList();
        expect(context, "all virtual rows have a chart", charts.size(), 24);
        expect(context, "every chart explicitly disables texture smoothing", charts.stream().allMatch(chart -> chart.contains("blur=\"false\"")), true);
        expect(context, "chart buffers retain one column per tick", charts.stream().allMatch(chart -> chart.contains("width=\"240\"") && chart.contains("height=\"32\"")), true);
        String mixins = new String(resource("/mxt.mixins.json"), StandardCharsets.UTF_8);
        expect(context, "the canvas hook is registered on the client", Pattern.compile("\"client\"\\s*:\\s*\\[[^]]*\"ApricityCanvasMixin\"").matcher(mixins).find(), true);

        // Read bytecode without loading client-only classes on the GameTest server.
        List<InvokeInstruction> calls = ClassFile.of().parse(resource("/com/sighs/apricityui/element/Canvas.class")).methods().stream()
                .filter(method -> method.methodName().equalsString("drawCanvas"))
                .flatMap(method -> method.code().stream()).flatMap(CompoundElement::elementStream)
                .filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
                .filter(call -> call.owner().asInternalName().equals("com/sighs/apricityui/render/ImageDrawer") && call.name().equalsString("draw"))
                .toList();
        expect(context, "the installed Canvas has exactly one sampling hook target", calls.size(), 1);
        expect(context, "the installed draw call retains the targeted blur argument", calls.getFirst().type().stringValue(),
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/sighs/apricityui/spi/TextureKey;FFFFZ)V");
    }

    private static byte[] resource(String path) {
        try (InputStream stream = Objects.requireNonNull(ProfilerChartVerifyProbes.class.getResourceAsStream(path), path)) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void redraw(ProbeContext context) {
        long[][] busy = new long[Profiler.WINDOW_TICKS][2];
        for (long[] sample : busy) {
            sample[0] = 4_000_000L;
            sample[1] = 1L;
        }
        boolean[] complete = new boolean[Profiler.WINDOW_TICKS];
        Arrays.fill(complete, true);
        complete[12] = false;
        ProfilerWindowSnapshot peak = window(busy, complete);
        complete[12] = true;
        ProfilerWindowSnapshot idle = window(new long[Profiler.WINDOW_TICKS][2], complete);
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
        ProfilerWindowSnapshot partial = window(new long[][]{{1_000_000L, 1L}}, new boolean[]{true});
        draw(reused, partial);
        draw(fresh, partial);
        expect(context, "a shorter window clears unused history", Arrays.equals(pixels(reused), pixels(fresh)), true);
    }

    private static ProfilerWindowSnapshot window(long[][] samples, boolean[] complete) {
        return ProfilerWindowSnapshot.restore(1L, 1L, 0L, List.of("server/cost"),
                List.of(new ProfilerWindowSnapshot.ProducerData(1L, "Server thread", 0L, samples, complete, 0L)));
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
