package com.iafenvoy.mxt.render.effect;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

// A borrowed entry cannot be reused or evicted until its lease is returned.
public final class EffectTargetPool<K, T extends AutoCloseable> implements AutoCloseable {
    private static final int IDLE_FRAMES = 120;
    private final long budget;
    private final int capacity;
    private final Consumer<Exception> closeFailure;
    private final List<Entry> entries = new ArrayList<>();
    private long bytes;
    private long frame;
    private boolean busy;
    private boolean closed;

    public EffectTargetPool(long budget, int capacity, Consumer<Exception> closeFailure) {
        if (budget < 0 || capacity < 1) throw new IllegalArgumentException("Invalid effect target pool limits");
        this.budget = budget;
        this.capacity = capacity;
        this.closeFailure = Objects.requireNonNull(closeFailure);
    }

    public @Nullable Lease acquire(K key, long bytes, Supplier<T> factory) {
        if (this.closed || this.busy || bytes <= 0 || bytes > this.budget) return null;
        this.busy = true;
        try {
            for (Entry entry : this.entries)
                if (!entry.borrowed && entry.bytes == bytes && entry.key.equals(key)) {
                    entry.borrowed = true;
                    return new Lease(entry);
                }
            while (this.entries.size() >= this.capacity || bytes > this.budget - this.bytes) {
                Entry oldest = null;
                for (Entry entry : this.entries)
                    if (!entry.borrowed && (oldest == null || entry.lastUsed < oldest.lastUsed)) oldest = entry;
                if (oldest == null) return null;
                this.remove(oldest);
                if (this.closed) return null;
            }
            T value = Objects.requireNonNull(factory.get());
            if (this.closed) {
                this.discard(value);
                return null;
            }
            Entry entry = new Entry(key, bytes, value);
            this.entries.add(entry);
            this.bytes += bytes;
            return new Lease(entry);
        } finally {
            this.busy = false;
        }
    }

    public long bytes() {
        return this.bytes;
    }

    public void endFrame() {
        if (this.closed || this.busy) return;
        this.frame++;
        this.busy = true;
        try {
            for (Entry entry : List.copyOf(this.entries)) {
                if (this.closed) break;
                if (!entry.borrowed && this.frame - entry.lastUsed >= IDLE_FRAMES) this.remove(entry);
            }
        } finally {
            this.busy = false;
        }
    }

    private void remove(Entry entry) {
        this.entries.remove(entry);
        this.bytes -= entry.bytes;
        T previous = entry.value;
        entry.value = null;
        if (previous != null) this.discard(previous);
    }

    private void discard(T value) {
        try {
            value.close();
        } catch (Exception exception) {
            this.closeFailure.accept(exception);
        }
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        while (!this.entries.isEmpty()) this.remove(this.entries.getLast());
    }

    private final class Entry {
        private final K key;
        private final long bytes;
        private @Nullable T value;
        private boolean borrowed = true;
        private long lastUsed = EffectTargetPool.this.frame;

        private Entry(K key, long bytes, @Nullable T value) {
            this.key = key;
            this.bytes = bytes;
            this.value = value;
        }
    }

    public final class Lease implements AutoCloseable {
        private @Nullable Entry entry;

        private Lease(@Nullable Entry entry) {
            this.entry = entry;
        }

        public @Nullable T get() {
            return this.entry == null ? null : this.entry.value;
        }

        @Override
        public void close() {
            Entry previous = this.entry;
            this.entry = null;
            if (previous != null) {
                previous.lastUsed = EffectTargetPool.this.frame;
                previous.borrowed = false;
            }
        }
    }
}
