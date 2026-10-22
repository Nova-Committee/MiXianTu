package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.util.CloseableResources;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CloseableResourcesVerifyProbes {
    private CloseableResourcesVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/render_resources/lifecycle", CloseableResourcesVerifyProbes::lifecycle),
                Probe.of("verify/render_resources/failures", CloseableResourcesVerifyProbes::failures),
                Probe.of("verify/render_resources/shutdown", CloseableResourcesVerifyProbes::shutdown),
                Probe.of("verify/render_resources/thread", CloseableResourcesVerifyProbes::thread));
    }

    private static void lifecycle(ProbeContext context) {
        AtomicBoolean available = new AtomicBoolean();
        AtomicInteger created = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        List<Exception> failures = new ArrayList<>();
        try (CloseableResources resources = new CloseableResources(() -> {
        }, available::get, failures::add)) {
            CloseableResources.Slot<AutoCloseable> slot = resources.register(() -> {
                created.incrementAndGet();
                return closed::incrementAndGet;
            });
            CloseableResources.Slot<AutoCloseable> unused = resources.register(() -> {
                throw new AssertionError("An unused slot must not allocate");
            });
            context.expect("registration does not construct resources", created.get(), 0);
            context.expect("a worldless acquisition is empty", slot.get(), null);
            available.set(true);
            context.expect("peek never constructs resources", slot.peek(), null);
            AutoCloseable first = slot.get();
            context.expect("one active acquisition constructs one resource", created.get(), 1);
            context.expect("subsequent acquisition reuses the same resource", slot.get() == first, true);
            context.expect("peek sees the current resource", slot.peek() == first, true);
            available.set(false);
            context.expect("an inactive owner cannot draw a cached resource", slot.peek(), null);
            resources.release();
            resources.release();
            context.expect("unload releases once despite duplicate notifications", closed.get(), 1);
            available.set(true);
            context.expect("a later world reconstructs a resource", slot.get() != null, true);
            context.expect("exactly two resources have been constructed", created.get(), 2);
            slot.release();
            slot.release();
            context.expect("explicit release is also idempotent", closed.get(), 2);
            context.expect("an unused slot remains empty after unloading", unused.peek(), null);
            slot.get();
        }
        context.expect("shutdown releases the final reconstructed resource", closed.get(), 3);
        context.expect("successful ownership produces no close failures", failures.size(), 0);
    }

    private static void failures(ProbeContext context) {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        List<Exception> failures = new ArrayList<>();
        IllegalStateException constructionFailure = new IllegalStateException("construction probe");
        IllegalStateException closeFailure = new IllegalStateException("close probe");
        try (CloseableResources resources = new CloseableResources(() -> {
        }, () -> true, failures::add)) {
            CloseableResources.Slot<AutoCloseable> retry = resources.register(() -> {
                if (attempts.incrementAndGet() == 1) throw constructionFailure;
                return closed::incrementAndGet;
            });
            RuntimeException caught = null;
            try {
                retry.get();
            } catch (RuntimeException exception) {
                caught = exception;
            }
            context.expect("construction preserves the original failure", caught == constructionFailure, true);
            context.expect("a failed construction is never cached", retry.peek(), null);
            context.expect("the next acquisition can retry construction", retry.get() != null, true);
            context.expect("construction was retried exactly once", attempts.get(), 2);

            AtomicReference<CloseableResources.Slot<AutoCloseable>> brokenRef = new AtomicReference<>();
            CloseableResources.Slot<AutoCloseable> broken = resources.register(() -> () -> {
                context.expect("close first detaches the resource", brokenRef.get().peek(), null);
                context.expect("close cannot reenter acquisition", brokenRef.get().get(), null);
                closed.incrementAndGet();
                throw closeFailure;
            });
            brokenRef.set(broken);
            CloseableResources.Slot<AutoCloseable> later = resources.register(() -> closed::incrementAndGet);
            broken.get();
            later.get();
            resources.release();
            resources.release();
            context.expect("one failed close does not skip the remaining resource", closed.get(), 3);
            context.expect("the original close failure is reported exactly once", failures, List.of(closeFailure));
            context.expect("a failed close is never retained for drawing", broken.peek(), null);
        }
    }

    private static void shutdown(ProbeContext context) {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        List<Exception> failures = new ArrayList<>();
        CloseableResources resources = new CloseableResources(() -> {
        }, () -> true, failures::add);
        AtomicReference<CloseableResources.Slot<AutoCloseable>> reference = new AtomicReference<>();
        CloseableResources.Slot<AutoCloseable> slot = resources.register(() -> {
            created.incrementAndGet();
            return () -> {
                closed.incrementAndGet();
                context.expect("shutdown disables acquisition before closing", reference.get().get(), null);
                resources.close();
            };
        });
        reference.set(slot);
        slot.get();
        resources.close();
        resources.close();
        resources.release();
        context.expect("terminal shutdown closes once even when reentered", closed.get(), 1);
        context.expect("a later render cannot reopen the resource", slot.get(), null);
        CloseableResources.Slot<AutoCloseable> late = resources.register(() -> {
            created.incrementAndGet();
            return closed::incrementAndGet;
        });
        context.expect("late registration cannot revive a closed owner", late.get(), null);
        context.expect("no allocation occurs after shutdown", created.get(), 1);

        CloseableResources interrupted = new CloseableResources(() -> {
        }, () -> true, failures::add);
        CloseableResources.Slot<AutoCloseable> interruptedSlot = interrupted.register(() -> {
            interrupted.close();
            return closed::incrementAndGet;
        });
        context.expect("shutdown during construction does not publish a resource", interruptedSlot.get(), null);
        context.expect("a resource completed during shutdown is released", closed.get(), 2);
        interrupted.close();

        CloseableResources unloaded = new CloseableResources(() -> {
        }, () -> true, failures::add);
        CloseableResources.Slot<AutoCloseable> unloadedSlot = unloaded.register(() -> {
            unloaded.release();
            return closed::incrementAndGet;
        });
        context.expect("unload during construction invalidates that acquisition", unloadedSlot.get(), null);
        context.expect("an invalidated construction is also released", closed.get(), 3);
        unloaded.close();
        context.expect("shutdown and reentrancy produce no close failures", failures.size(), 0);
    }

    private static void thread(ProbeContext context) {
        Thread owner = Thread.currentThread();
        AtomicInteger created = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Exception> failures = new ArrayList<>();
        try (CloseableResources resources = new CloseableResources(() -> {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong resource thread");
        }, () -> true, failures::add)) {
            CloseableResources.Slot<AutoCloseable> slot = resources.register(() -> {
                created.incrementAndGet();
                return closed::incrementAndGet;
            });
            slot.get();
            Thread worker = Thread.ofPlatform().start(() -> {
                for (Runnable operation : List.<Runnable>of(slot::get, slot::peek, slot::release, resources::release, resources::close)) {
                    try {
                        operation.run();
                    } catch (IllegalStateException expected) {
                        rejected.incrementAndGet();
                    }
                }
            });
            try {
                worker.join(5000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            context.expect("the worker finishes without waiting on the owner", worker.isAlive(), false);
            context.expect("every off-thread access is rejected", rejected.get(), 5);
            context.expect("off-thread calls never allocate another resource", created.get(), 1);
            context.expect("off-thread calls never release the live resource", closed.get(), 0);
            context.expect("a rejected shutdown leaves the owner active", slot.get() != null, true);
        }
        context.expect("the owner thread can still close normally", closed.get(), 1);
        context.expect("thread rejection is not reported as a close failure", failures.size(), 0);
    }
}
