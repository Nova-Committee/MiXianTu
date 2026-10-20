package com.iafenvoy.mxt.util.profiling;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

// Own an instance per logical side and close it when its owner stops; construction starts no worker.
public final class Profiler implements AutoCloseable {
    public static final int WINDOW_TICKS = 240;
    private static final int MAX_DEPTH = 128;
    private static final int MAX_SPANS = 16_384;
    private static final ProfilerScope NOOP = () -> {
    };

    private final ProfilerSources sources = new ProfilerSources();
    private final AtomicLong nextProducer = new AtomicLong();
    private final ThreadLocal<State> states = new ThreadLocal<>();
    private final LongSupplier clock;
    private volatile ProfilerAggregator session;
    private long generation;
    private boolean closed;

    public Profiler() {
        this(System::nanoTime);
    }

    public Profiler(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public ProfilerSources sources() {
        return this.sources;
    }

    public boolean enabled() {
        ProfilerAggregator current = this.session;
        return current != null && current.active();
    }

    public synchronized void setEnabled(boolean enabled) {
        if (enabled && this.closed) throw new IllegalStateException("The profiler is closed");
        if (enabled && this.enabled()) return;
        ProfilerAggregator previous = this.session;
        this.session = null;
        if (previous != null) previous.stop();
        if (enabled) {
            ProfilerAggregator current = new ProfilerAggregator(++this.generation, this.sources);
            this.session = current;
            current.start();
        }
    }

    public ProfilerScope scope(ProfilerSource source) {
        ProfilerAggregator current = this.session;
        if (current == null || !current.active()) return NOOP;
        Objects.requireNonNull(source);
        if (source.registry() != this.sources)
            throw new IllegalArgumentException("Source belongs to a different profiler");
        State state = this.state(current);
        if (state.depth == MAX_DEPTH) {
            state.discarded++;
            return NOOP;
        }
        if (state.depth == state.tokens.length) state.growStack();
        int depth = state.depth++;
        long token = ++state.nextToken;
        state.tokens[depth] = token;
        state.started[depth] = this.clock.getAsLong();
        return new Scope(state, current.generation(), depth, token, source.index());
    }

    // Flush once per completed tick on every producer thread, including ticks with no spans.
    public void flushTick() {
        ProfilerAggregator current = this.session;
        if (current == null || !current.active()) return;
        State state = this.state(current);
        long tick = state.tick++;
        if (state.depth != 0) {
            state.discarded += state.depth;
            state.depth = 0;
        }
        current.submit(state.producer, state.name, tick, state.ids, state.nanos, state.size, state.discarded);
        state.size = 0;
        state.discarded = 0;
    }

    // The consumer captures raw samples after queued batches; statistics are evaluated only by the reader.
    public CompletableFuture<ProfilerWindowSnapshot> snapshot() {
        ProfilerAggregator current = this.session;
        return current == null ? CompletableFuture.completedFuture(ProfilerWindowSnapshot.EMPTY) : current.snapshot();
    }

    public void releaseThread() {
        this.states.remove();
    }

    @Override
    public synchronized void close() {
        this.setEnabled(false);
        this.closed = true;
        this.states.remove();
    }

    private State state(ProfilerAggregator current) {
        State state = this.states.get();
        if (state == null) {
            state = new State(this.nextProducer.getAndIncrement(), Thread.currentThread().getName());
            this.states.set(state);
        }
        if (state.generation != current.generation()) state.reset(current.generation());
        return state;
    }

    private static final class State {
        private final long producer;
        private final String name;
        private final Thread thread = Thread.currentThread();
        private long generation;
        private long tick;
        private long nextToken;
        private long[] tokens = new long[16];
        private long[] started = new long[16];
        private int depth;
        private int[] ids = new int[256];
        private long[] nanos = new long[256];
        private int size;
        private long discarded;

        private State(long producer, String name) {
            this.producer = producer;
            this.name = name;
        }

        private void reset(long generation) {
            this.generation = generation;
            this.tick = 0;
            this.depth = 0;
            this.size = 0;
            this.discarded = 0;
        }

        private void growStack() {
            this.tokens = Arrays.copyOf(this.tokens, this.tokens.length * 2);
            this.started = Arrays.copyOf(this.started, this.started.length * 2);
        }

        private void record(int source, long duration) {
            if (this.size == MAX_SPANS) {
                this.discarded++;
                return;
            }
            if (this.size == this.ids.length) {
                this.ids = Arrays.copyOf(this.ids, this.ids.length * 2);
                this.nanos = Arrays.copyOf(this.nanos, this.nanos.length * 2);
            }
            this.ids[this.size] = source;
            this.nanos[this.size++] = duration;
        }
    }

    private final class Scope implements ProfilerScope {
        private final State state;
        private final long generation;
        private final int depth;
        private final long token;
        private final int source;
        private boolean finished;

        private Scope(State state, long generation, int depth, long token, int source) {
            this.state = state;
            this.generation = generation;
            this.depth = depth;
            this.token = token;
            this.source = source;
        }

        @Override
        public void close() {
            // A wrong-thread close cannot write the producer's state or prevent its later correct close.
            if (this.finished || Thread.currentThread() != this.state.thread) return;
            this.finished = true;
            ProfilerAggregator current = Profiler.this.session;
            if (current == null || !current.active() || current.generation() != this.generation
                    || this.state.generation != this.generation) return;
            if (this.state.depth <= this.depth || this.state.tokens[this.depth] != this.token) return;
            if (this.state.depth != this.depth + 1) {
                this.state.discarded += this.state.depth - this.depth;
                this.state.depth = this.depth;
                return;
            }
            this.state.depth--;
            this.state.record(this.source, Math.max(0L, Profiler.this.clock.getAsLong() - this.state.started[this.depth]));
        }
    }
}
