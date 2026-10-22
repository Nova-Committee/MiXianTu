package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.particle.LightBeamParticleOptions;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.render.effect.EffectFrame;
import com.iafenvoy.mxt.render.effect.EffectTargetPool;
import com.iafenvoy.mxt.render.particle.LightBeamParticleDraws;
import com.iafenvoy.mxt.render.particle.LightBeamParticleFrame;
import com.iafenvoy.mxt.render.particle.LightBeamParticleMesh;
import com.iafenvoy.mxt.render.particle.LightBeamParticleShape;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldEffectsVerifyProbes {
    private WorldEffectsVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/world_effects/frame", WorldEffectsVerifyProbes::frame),
                Probe.of("verify/world_effects/targets", WorldEffectsVerifyProbes::targets),
                Probe.of("verify/world_effects/failures", WorldEffectsVerifyProbes::failures),
                Probe.of("verify/world_effects/draw_order", WorldEffectsVerifyProbes::drawOrder));
    }

    private static void frame(ProbeContext context) {
        Object view = new Object();
        List<Exception> failures = new ArrayList<>();
        AtomicInteger copies = new AtomicInteger(), closes = new AtomicInteger();
        try (EffectFrame<String, AutoCloseable> unused = new EffectFrame<>(view, 7, failures::add)) {
            unused.capture(() -> {
                copies.incrementAndGet();
                return closes::incrementAndGet;
            });
            context.expect("zero demand performs zero copies", copies.get(), 0);
            context.expect("a demand after capture is rejected", unused.requestDepth(), false);
        }
        EffectFrame<String, AutoCloseable> frame = new EffectFrame<>(view, 7, failures::add);
        context.expect("same view and generation match", frame.matches(view, 7), true);
        context.expect("a different view cannot share the frame", frame.matches(new Object(), 7), false);
        context.expect("a later generation cannot share the frame", frame.matches(view, 8), false);
        frame.requestDepth();
        frame.requestDepth();
        AutoCloseable depth = frame.capture(() -> {
            copies.incrementAndGet();
            return closes::incrementAndGet;
        });
        context.expect("consumers share one capture", frame.capture(() -> {
            throw new AssertionError("duplicate capture");
        }) == depth, true);
        context.expect("multiple requests perform one copy", copies.get(), 1);
        String a = "equal";
        context.expect("first submission is accepted", frame.submit(a), true);
        context.expect("same task identity is rejected", frame.submit(a), false);
        context.expect("distinct equal tasks are retained", frame.submit(a), true);
        List<String> delivered = new ArrayList<>();
        frame.drain(delivered::add);
        frame.drain(_ -> {
            throw new AssertionError("duplicate drain");
        });
        context.expect("draw order preserves both identities", delivered.size() == 2 && Objects.equals(delivered.get(0), a) && Objects.equals(delivered.get(1), a), true);
        context.expect("a drained frame refuses new work", frame.submit("late"), false);
        frame.close();
        frame.close();
        context.expect("depth is returned exactly once", closes.get(), 1);
        context.expect("a closed frame loses its view credential", frame.matches(view, 7), false);
        context.expect("closed frames expose no depth", frame.resource(), null);
        context.expect("successful frames report no cleanup failures", failures.size(), 0);
        try (EffectFrame<Integer, AutoCloseable> bounded = new EffectFrame<>(view, 8, failures::add)) {
            int accepted = 0;
            for (int i = 0; i < 1025; i++) if (bounded.submit(i)) accepted++;
            context.expect("stage task count is bounded", accepted, 1024);
        }
    }

    private static void targets(ProbeContext context) {
        List<Exception> failures = new ArrayList<>();
        AtomicInteger closes = new AtomicInteger();
        EffectTargetPool<String, AutoCloseable> pool = new EffectTargetPool<>(12, 3, failures::add);
        EffectTargetPool<String, AutoCloseable>.Lease a = pool.acquire("depth", 4, () -> closes::incrementAndGet);
        EffectTargetPool<String, AutoCloseable>.Lease b = pool.acquire("depth", 4, () -> closes::incrementAndGet);
        context.expect("simultaneous views get separate targets", a != null && b != null && a.get() != b.get(), true);
        context.expect("overbudget cannot evict active targets", pool.acquire("color", 8, () -> {
            throw new AssertionError("overbudget factory");
        }), null);
        AutoCloseable first = a.get();
        a.close();
        a.close();
        EffectTargetPool<String, AutoCloseable>.Lease reused = pool.acquire("depth", 4, () -> {
            throw new AssertionError("failed reuse");
        });
        context.expect("an idle exact match is reused", reused != null && reused.get() == first, true);
        reused.close();
        EffectTargetPool<String, AutoCloseable>.Lease color = pool.acquire("color", 8, () -> closes::incrementAndGet);
        context.expect("idle depth is evicted to fit another format", color != null && closes.get() == 1, true);
        context.expect("borrowed depth remains alive", b.get() != null, true);
        context.expect("budget counts all live textures", pool.bytes(), 12L);
        for (int i = 0; i < 121; i++) pool.endFrame();
        context.expect("borrowed targets survive idle collection", pool.bytes(), 12L);
        b.close();
        color.close();
        for (int i = 0; i < 119; i++) pool.endFrame();
        context.expect("idle targets survive until the deadline", pool.bytes(), 12L);
        pool.endFrame();
        context.expect("expired idle targets release their budget", pool.bytes(), 0L);
        context.expect("every allocated target closes once", closes.get(), 3);
        EffectTargetPool<String, AutoCloseable>.Lease outstanding = pool.acquire("new", 4, () -> closes::incrementAndGet);
        pool.close();
        pool.close();
        context.expect("shutdown invalidates outstanding leases", outstanding.get(), null);
        outstanding.close();
        context.expect("shutdown releases borrowed targets once", closes.get(), 4);
        context.expect("closed pool refuses allocations", pool.acquire("new", 4, () -> {
            throw new AssertionError("closed pool factory");
        }), null);
        context.expect("successful target cleanup reports no failures", failures.size(), 0);
    }

    private static void failures(ProbeContext context) {
        List<Exception> failures = new ArrayList<>();
        AtomicInteger closes = new AtomicInteger(), calls = new AtomicInteger();
        EffectFrame<Integer, AutoCloseable> frame = new EffectFrame<>(new Object(), 1, failures::add);
        frame.requestDepth();
        frame.capture(() -> {
            frame.close();
            return closes::incrementAndGet;
        });
        context.expect("capture construction reentry releases its result", closes.get(), 1);
        context.expect("capture construction reentry publishes nothing", frame.resource(), null);
        try (EffectFrame<Integer, AutoCloseable> earlyDrain = new EffectFrame<>(new Object(), 2, failures::add)) {
            earlyDrain.requestDepth();
            earlyDrain.capture(() -> {
                earlyDrain.drain(_ -> {
                });
                return closes::incrementAndGet;
            });
            context.expect("draining during capture also releases its late result", closes.get(), 2);
            context.expect("a late capture cannot publish after drain", earlyDrain.resource(), null);
        }
        try (EffectFrame<Integer, AutoCloseable> drain = new EffectFrame<>(new Object(), 2, failures::add)) {
            drain.requestDepth();
            drain.submit(1);
            drain.submit(2);
            drain.drain(_ -> {
                calls.incrementAndGet();
                drain.close();
            });
            context.expect("closing a frame during drain cancels remaining work", calls.get(), 1);
            drain.capture(() -> {
                throw new AssertionError("capture after drain");
            });
        }
        try (EffectFrame<Integer, AutoCloseable> failed = new EffectFrame<>(new Object(), 3, failures::add)) {
            failed.requestDepth();
            try {
                failed.capture(() -> {
                    throw new IllegalStateException("capture probe");
                });
            } catch (IllegalStateException expected) {
                calls.incrementAndGet();
            }
            failed.capture(() -> {
                throw new AssertionError("capture retry");
            });
        }
        context.expect("capture failure propagates once without a same-frame retry", calls.get(), 2);
        EffectTargetPool<String, AutoCloseable> pool = new EffectTargetPool<>(8, 2, failures::add);
        try {
            pool.acquire("failed", 8, () -> {
                throw new IllegalStateException("allocation probe");
            });
        } catch (IllegalStateException expected) {
            calls.incrementAndGet();
        }
        context.expect("failed allocation consumes no budget", pool.bytes(), 0L);
        EffectTargetPool<String, AutoCloseable>.Lease bad = pool.acquire("bad", 4, () -> () -> {
            throw new IllegalStateException("close probe");
        });
        EffectTargetPool<String, AutoCloseable>.Lease good = pool.acquire("good", 4, () -> closes::incrementAndGet);
        pool.close();
        context.expect("cleanup continues after an individual close fails", closes.get(), 3);
        context.expect("close failure is reported once", failures.size(), 1);
        context.expect("failed close leaves no budget", pool.bytes(), 0L);
        context.expect("both leases are invalid after shutdown", bad.get() == null && good.get() == null, true);
        EffectTargetPool<String, AutoCloseable> reentrant = new EffectTargetPool<>(4, 1, failures::add);
        EffectTargetPool<String, AutoCloseable>.Lease discarded = reentrant.acquire("reentry", 4, () -> {
            reentrant.close();
            return closes::incrementAndGet;
        });
        context.expect("shutdown during construction does not publish a lease", discarded, null);
        context.expect("shutdown during construction releases its allocation", closes.get(), 4);
        context.expect("shutdown during construction leaves no budget", reentrant.bytes(), 0L);
        EffectTargetPool<String, AutoCloseable> eviction = new EffectTargetPool<>(4, 1, failures::add);
        EffectTargetPool<String, AutoCloseable>.Lease evicted = eviction.acquire("old", 4, () -> eviction);
        evicted.close();
        context.expect("shutdown from eviction prevents further allocation", eviction.acquire("new", 4, () -> {
            throw new AssertionError("allocation after eviction shutdown");
        }), null);
    }

    private static void drawOrder(ProbeContext context) {
        LightBeamParticleDraws draws = new LightBeamParticleDraws();
        LightBeamParticleShape shape = new LightBeamParticleShape(new LightBeamParticleOptions(-1, 0, 0, 0, 6, .18F,
                LightBeamParticleOptions.FlowDirection.UP, 1, 40, false, 1, 16), 0, 0, 0, 1);
        List<LightBeamParticleFrame.Entry> entries = new ArrayList<>();
        for (int i = 0; i < LightBeamParticleMesh.MAX_INSTANCES; i++) {
            LightBeamParticleShape.Segment segment = new LightBeamParticleShape.Segment(i % 2 == 0 ? 0 : -1, i, Vec3.ZERO, 0, 1, 1, 1, 0, -1, 1);
            entries.add(new LightBeamParticleFrame.Entry(shape, segment, i));
        }
        draws.build(entries, false);
        context.expect("basic path keeps 128 draws at full capacity", draws.size(), 128);
        int failures = 0, visited = 0;
        for (int i = 0; i < draws.size(); i++) {
            if (draws.first(i) != 0 || draws.count(i) != 64 || draws.glow(i)) failures++;
            visited += draws.count(i);
        }
        context.expect("basic batches contain all records exactly once", visited, 8192);
        context.expect("basic batch boundaries remain unchanged", failures, 0);
        draws.build(entries, true);
        context.expect("alternating pipelines have a bounded worst case", draws.size(), 8192);
        failures = 0;
        for (int i = 0; i < draws.size(); i++)
            if (draws.batch(i) * 64 + draws.first(i) != i || draws.count(i) != 1 || draws.glow(i) == (i % 2 == 0))
                failures++;
        context.expect("depth pipeline changes preserve every record's global order", failures, 0);
        draws.build(entries.subList(0, 65), true);
        context.expect("the final partial batch starts at its own selector", draws.batch(64) == 1 && draws.first(64) == 0 && draws.count(64) == 1, true);
        List<LightBeamParticleFrame.Entry> same = new ArrayList<>();
        for (int i = 0; i < 65; i++) same.add(entries.get(1));
        draws.build(same, true);
        context.expect("adjacent glow records merge within each UBO", draws.size() == 2 && draws.count(0) == 64 && draws.count(1) == 1, true);
        context.expect("merged glow draws select the glow pipeline", draws.glow(0) && draws.glow(1), true);
        draws.build(List.of(), true);
        context.expect("an empty view clears previous draw commands", draws.size(), 0);
    }
}
