package com.iafenvoy.mxt.util.profiling;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

final class ProfilerAggregator {
    private static final int QUEUE_CAPACITY = 64;
    private final long generation;
    private final ProfilerSources sources;
    private final ConcurrentLinkedQueue<Batch> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong droppedBatches = new AtomicLong();
    private final Map<Long, Window> windows = new LinkedHashMap<>();
    private final List<CompletableFuture<ProfilerWindowSnapshot>> requests = new ArrayList<>();
    private final List<CompletableFuture<ProfilerWindowSnapshot>> inFlight = new ArrayList<>();
    private final Thread worker;
    private volatile boolean active = true;
    private long version;

    ProfilerAggregator(long generation, ProfilerSources sources) {
        this.generation = generation;
        this.sources = sources;
        this.worker = new Thread(this::run, "MXT profiler");
        this.worker.setDaemon(true);
    }

    long generation() {
        return this.generation;
    }

    boolean active() {
        return this.active;
    }

    void start() {
        this.worker.start();
    }

    void stop() {
        List<CompletableFuture<ProfilerWindowSnapshot>> cancelled;
        synchronized (this.requests) {
            this.active = false;
            cancelled = new ArrayList<>(this.requests);
            cancelled.addAll(this.inFlight);
            this.requests.clear();
            this.inFlight.clear();
        }
        LockSupport.unpark(this.worker);
        cancelled.forEach(request -> request.complete(ProfilerWindowSnapshot.EMPTY));
    }

    // The submission boundary rejects disabled sessions even if sampling raced with a toggle.
    void submit(long producer, String name, long tick, int[] ids, long[] nanos, int size, long discarded) {
        if (!this.active) return;
        int count;
        do {
            count = this.pending.get();
            if (count >= QUEUE_CAPACITY) {
                this.droppedBatches.incrementAndGet();
                return;
            }
        } while (!this.pending.compareAndSet(count, count + 1));
        this.queue.offer(new Batch(producer, name, tick, Arrays.copyOf(ids, size), Arrays.copyOf(nanos, size), discarded));
        LockSupport.unpark(this.worker);
    }

    CompletableFuture<ProfilerWindowSnapshot> snapshot() {
        synchronized (this.requests) {
            if (!this.active) return CompletableFuture.completedFuture(ProfilerWindowSnapshot.EMPTY);
            CompletableFuture<ProfilerWindowSnapshot> request = new CompletableFuture<>();
            this.requests.add(request);
            LockSupport.unpark(this.worker);
            return request;
        }
    }

    private void run() {
        try {
            while (this.active) {
                // Bound a drain so continuous producers cannot starve explicit snapshot requests.
                for (int remaining = QUEUE_CAPACITY; remaining > 0; remaining--) {
                    Batch batch = this.queue.poll();
                    if (batch == null) break;
                    this.pending.decrementAndGet();
                    this.windows.computeIfAbsent(batch.producer(), id -> new Window(id, batch.name()))
                            .add(batch, this.sources.size());
                    this.version++;
                }
                List<CompletableFuture<ProfilerWindowSnapshot>> readers;
                synchronized (this.requests) {
                    readers = this.requests.isEmpty() ? List.of() : List.copyOf(this.requests);
                    this.requests.clear();
                    this.inFlight.addAll(readers);
                }
                if (!readers.isEmpty()) {
                    List<ProfilerSource> registered = this.sources.all();
                    ProfilerWindowSnapshot snapshot = this.active
                            ? new ProfilerWindowSnapshot(this.generation, this.version, registered,
                            this.windows.values().stream().map(window -> window.snapshot(registered)).toList(),
                            this.droppedBatches.get())
                            : ProfilerWindowSnapshot.EMPTY;
                    readers.forEach(reader -> reader.complete(snapshot));
                    synchronized (this.requests) {
                        this.inFlight.removeAll(readers);
                    }
                }
                if (this.queue.isEmpty()) LockSupport.park(this);
            }
        } finally {
            this.stop();
            this.queue.clear();
            this.windows.clear();
        }
    }

    private record Batch(long producer, String name, long tick, int[] ids, long[] nanos, long discarded) {
    }

    private static final class Window {
        private final long producer;
        private final String name;
        private final long[] ticks = new long[Profiler.WINDOW_TICKS];
        private final long[] discarded = new long[Profiler.WINDOW_TICKS];
        private final long[][] samples = new long[Profiler.WINDOW_TICKS][];
        private long nextTick;

        private Window(long producer, String name) {
            this.producer = producer;
            this.name = name;
            Arrays.fill(this.ticks, -1L);
        }

        private void add(Batch batch, int sourceCount) {
            int slot = (int) (batch.tick() % Profiler.WINDOW_TICKS);
            long[] values = new long[sourceCount * 2];
            for (int index = 0; index < batch.ids().length; index++) {
                int source = batch.ids()[index];
                values[source * 2] += batch.nanos()[index];
                values[source * 2 + 1]++;
            }
            this.samples[slot] = values;
            this.ticks[slot] = batch.tick();
            this.discarded[slot] = batch.discarded();
            this.nextTick = batch.tick() + 1;
        }

        private ProfilerWindowSnapshot.Producer snapshot(List<ProfilerSource> sources) {
            int count = (int) Math.min(this.nextTick, Profiler.WINDOW_TICKS);
            long firstTick = this.nextTick - count;
            long[][] samples = new long[count][];
            boolean[] complete = new boolean[count];
            long discardedSpans = 0L;
            for (int index = 0; index < count; index++) {
                long tick = firstTick + index;
                int slot = (int) (tick % Profiler.WINDOW_TICKS);
                if (this.ticks[slot] == tick) {
                    samples[index] = Arrays.copyOf(this.samples[slot], sources.size() * 2);
                    complete[index] = this.discarded[slot] == 0L;
                    discardedSpans += this.discarded[slot];
                } else samples[index] = new long[sources.size() * 2];
            }
            return new ProfilerWindowSnapshot.Producer(this.producer, this.name, firstTick, sources, samples, complete, discardedSpans);
        }
    }
}
