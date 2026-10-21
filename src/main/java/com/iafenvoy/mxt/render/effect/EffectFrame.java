package com.iafenvoy.mxt.render.effect;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

// CPU-only view ownership; capture and queued draws are each consumed at most once.
public final class EffectFrame<D, R extends AutoCloseable> implements AutoCloseable {
    private static final int MAX_DRAWS = 1024;
    private final Object view;
    private final long generation;
    private final Consumer<Exception> closeFailure;
    private final List<D> draws = new ArrayList<>();
    private final Set<D> submitted = Collections.newSetFromMap(new IdentityHashMap<>());
    private @Nullable R resource;
    private boolean depthRequested;
    private boolean captured;
    private boolean drained;
    private boolean closed;

    public EffectFrame(Object view, long generation, Consumer<Exception> closeFailure) {
        this.view = view;
        this.generation = generation;
        this.closeFailure = closeFailure;
    }

    public boolean matches(Object view, long generation) {
        return !this.closed && this.view == view && this.generation == generation;
    }

    public boolean requestDepth() {
        if (this.closed || this.drained) return false;
        if (this.depthRequested) return true;
        if (this.captured) return false;
        this.depthRequested = true;
        return true;
    }

    public @Nullable R capture(Supplier<@Nullable R> factory) {
        if (this.closed || this.drained || this.captured) return this.resource;
        this.captured = true;
        if (this.depthRequested) {
            R created = factory.get();
            if (this.closed || this.drained) this.discard(created);
            else this.resource = created;
        }
        return this.resource;
    }

    public @Nullable R resource() {
        return this.closed ? null : this.resource;
    }

    public boolean submit(D draw) {
        if (this.closed || this.drained || this.draws.size() >= MAX_DRAWS || !this.submitted.add(draw)) return false;
        this.draws.add(draw);
        return true;
    }

    public void drain(Consumer<D> consumer) {
        if (this.closed || this.drained) return;
        this.drained = true;
        List<D> pending = List.copyOf(this.draws);
        this.draws.clear();
        try {
            for (D draw : pending) {
                if (this.closed) break;
                consumer.accept(draw);
            }
        } finally {
            this.draws.clear();
            this.submitted.clear();
        }
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.draws.clear();
        this.submitted.clear();
        R previous = this.resource;
        this.resource = null;
        this.discard(previous);
    }

    private void discard(@Nullable R previous) {
        if (previous != null) try {
            previous.close();
        } catch (Exception exception) {
            this.closeFailure.accept(exception);
        }
    }
}
