package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.util.profiling.Profiler;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ProfilerSource;
import com.iafenvoy.mxt.util.profiling.ProfilerWindowSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ProfilerVerifyProbes {
    private ProfilerVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/profiling/nesting", ProfilerVerifyProbes::nesting),
                Probe.of("verify/profiling/window", ProfilerVerifyProbes::window),
                Probe.of("verify/profiling/lifecycle", ProfilerVerifyProbes::lifecycle),
                Probe.of("verify/profiling/producers", ProfilerVerifyProbes::producers),
                Probe.of("verify/profiling/bounds", ProfilerVerifyProbes::bounds)
        );
    }

    private static void nesting(ProbeContext context) {
        AtomicLong clock = new AtomicLong();
        try (Profiler profiler = new Profiler(clock::get)) {
            ProfilerSource outer = profiler.sources().register("probe:outer");
            ProfilerSource inner = profiler.sources().register("probe:inner");
            expect(context, "sources reuse the same id", profiler.sources().register("probe:outer").id(), outer.id());
            expect(context, "the default switch is off", profiler.enabled(), false);
            try (ProfilerScope _ = profiler.scope(outer)) {
                clock.set(1L);
            }
            profiler.flushTick();
            expect(context, "disabled submission creates no sample", snapshot(profiler).producers().size(), 0);
            profiler.setEnabled(true);
            clock.set(0L);
            try (ProfilerScope _ = profiler.scope(outer)) {
                clock.set(5L);
                try (ProfilerScope _ = profiler.scope(inner)) {
                    clock.set(15L);
                }
                clock.set(25L);
            }
            profiler.flushTick();
            ProfilerWindowSnapshot.Producer sample = snapshot(profiler).producers().getFirst();
            expect(context, "the outer duration includes its child", sample.nanos(0, outer), 25L);
            expect(context, "the child records its own duration", sample.nanos(0, inner), 10L);
            expect(context, "the child was recorded once", sample.hits(0, inner), 1L);
        }
    }

    private static void window(ProbeContext context) {
        AtomicLong clock = new AtomicLong();
        try (Profiler profiler = new Profiler(clock::get)) {
            ProfilerSource source = profiler.sources().register("probe:window");
            profiler.setEnabled(true);
            ProfilerWindowSnapshot first = null;
            for (int tick = 0; tick < 245; tick++) {
                try (ProfilerScope _ = profiler.scope(source)) {
                    clock.addAndGet(tick + 1L);
                }
                profiler.flushTick();
                if (tick == 0) first = snapshot(profiler);
                if (tick % 16 == 0) snapshot(profiler);
            }
            ProfilerWindowSnapshot current = snapshot(profiler);
            ProfilerWindowSnapshot.Producer producer = current.producers().getFirst();
            ProfilerWindowSnapshot.Statistics stats = producer.statistics(source);
            expect(context, "the ring has exactly 240 ticks", producer.sampleCount(), 240);
            expect(context, "the ring evicts its first five ticks", producer.firstTick(), 5L);
            expect(context, "window min", stats.minNanos(), 6L);
            expect(context, "window average", stats.averageNanos(), 125.5D);
            expect(context, "window max", stats.maxNanos(), 245L);
            expect(context, "window median", stats.medianNanos(), 125L);
            expect(context, "window p95", stats.p95Nanos(), 233L);
            expect(context, "window p99", stats.p99Nanos(), 243L);
            expect(context, "old snapshots stay immutable", first.producers().getFirst().nanos(0, source), 1L);
            expect(context, "paced submissions dropped no batches", current.droppedBatches(), 0L);
            profiler.flushTick();
            producer = snapshot(profiler).producers().getFirst();
            expect(context, "an idle tick is complete", producer.complete(239), true);
            expect(context, "an idle tick records zero hits", producer.hits(239, source), 0L);
            expect(context, "idle ticks participate in statistics", producer.statistics(source).minNanos(), 0L);
        }
    }

    private static void lifecycle(ProbeContext context) {
        AtomicLong clock = new AtomicLong();
        try (Profiler profiler = new Profiler(clock::get)) {
            ProfilerSource source = profiler.sources().register("probe:lifecycle");
            profiler.setEnabled(true);
            ProfilerScope old = profiler.scope(source);
            profiler.setEnabled(false);
            expect(context, "off clears the read boundary", snapshot(profiler).producers().size(), 0);
            profiler.setEnabled(true);
            clock.set(20L);
            ProfilerScope fresh = profiler.scope(source);
            old.close();
            clock.set(50L);
            fresh.close();
            profiler.flushTick();
            expect(context, "an old close cannot consume the new frame",
                    snapshot(profiler).producers().getFirst().nanos(0, source), 30L);
            clock.set(60L);
            ProfilerScope next = profiler.scope(source);
            fresh.close();
            clock.set(70L);
            next.close();
            profiler.flushTick();
            expect(context, "a repeated close cannot consume a reused stack position",
                    snapshot(profiler).producers().getFirst().nanos(1, source), 10L);
        }
    }

    private static void producers(ProbeContext context) {
        try (Profiler profiler = new Profiler()) {
            ProfilerSource source = profiler.sources().register("probe:producer");
            profiler.setEnabled(true);
            Runnable produce = () -> {
                try {
                    for (int index = 0; index < 400; index++)
                        try (ProfilerScope _ = profiler.scope(source)) {
                        }
                    profiler.flushTick();
                } finally {
                    profiler.releaseThread();
                }
            };
            Thread first = Thread.ofPlatform().name("profiler-probe-a").start(produce);
            Thread second = Thread.ofPlatform().name("profiler-probe-b").start(produce);
            join(first);
            join(second);
            ProfilerWindowSnapshot result = snapshot(profiler);
            expect(context, "two producers have separate windows", result.producers().size(), 2);
            for (ProfilerWindowSnapshot.Producer producer : result.producers())
                expect(context, producer.name() + " preserves all hits through buffer growth", producer.hits(0, source), 400L);
        }
    }

    private static void bounds(ProbeContext context) {
        AtomicLong clock = new AtomicLong();
        try (Profiler profiler = new Profiler(clock::incrementAndGet)) {
            ProfilerSource source = profiler.sources().register("probe:bounds");
            profiler.setEnabled(true);
            ProfilerScope[] scopes = new ProfilerScope[129];
            for (int index = 0; index < scopes.length; index++) scopes[index] = profiler.scope(source);
            for (int index = scopes.length - 1; index >= 0; index--) scopes[index].close();
            profiler.flushTick();
            ProfilerWindowSnapshot.Producer producer = snapshot(profiler).producers().getFirst();
            expect(context, "excess nesting cannot pop its parent", producer.hits(0, source), 128L);
            expect(context, "excess nesting marks the sample incomplete", producer.complete(0), false);
            expect(context, "incomplete ticks do not distort percentiles", producer.statistics(source).samples(), 0);
            for (int index = 0; index < 17_000; index++)
                try (ProfilerScope _ = profiler.scope(source)) {
                }
            profiler.flushTick();
            producer = snapshot(profiler).producers().getFirst();
            expect(context, "the per-tick buffer has a hard limit", producer.hits(1, source), 16_384L);
            expect(context, "discarded spans are counted", producer.discardedSpans(), 617L);
            ProfilerScope crossing = profiler.scope(source);
            profiler.flushTick();
            ProfilerScope next = profiler.scope(source);
            crossing.close();
            next.close();
            profiler.flushTick();
            producer = snapshot(profiler).producers().getFirst();
            expect(context, "a cross-tick close cannot consume the next tick's frame", producer.hits(3, source), 1L);
        }
    }

    private static ProfilerWindowSnapshot snapshot(Profiler profiler) {
        try {
            return profiler.snapshot().get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Profiler snapshot did not finish", exception);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(5_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        if (thread.isAlive()) throw new IllegalStateException("Profiler producer did not finish");
    }

    private static void expect(ProbeContext context, String message, Object actual, Object expected) {
        context.expect(message, actual, expected);
        context.note(message + ": " + (Objects.equals(actual, expected) ? "OK" : "MISMATCH")
                + " actual=" + actual + " expected=" + expected);
    }
}
