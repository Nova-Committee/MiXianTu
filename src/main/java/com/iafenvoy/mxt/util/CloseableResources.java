package com.iafenvoy.mxt.util;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Registration may happen during parallel loading; resource access belongs to the checked owner thread.
public final class CloseableResources implements AutoCloseable {
    private final List<Slot<?>> slots = new CopyOnWriteArrayList<>();
    private final Runnable checkAccess;
    private final BooleanSupplier available;
    private final Consumer<Exception> closeFailure;
    private boolean releasing;
    private boolean closed;

    public CloseableResources(Runnable checkAccess, BooleanSupplier available, Consumer<Exception> closeFailure) {
        this.checkAccess = Objects.requireNonNull(checkAccess);
        this.available = Objects.requireNonNull(available);
        this.closeFailure = Objects.requireNonNull(closeFailure);
    }

    public <T extends AutoCloseable> Slot<T> register(Supplier<T> factory) {
        Slot<T> slot = new Slot<>(this, Objects.requireNonNull(factory));
        this.slots.add(slot);
        return slot;
    }

    public void release() {
        this.checkAccess.run();
        if (this.releasing) return;
        this.releasing = true;
        try {
            for (Slot<?> slot : this.slots) slot.release();
        } finally {
            this.releasing = false;
        }
    }

    @Override
    public void close() {
        this.checkAccess.run();
        this.closed = true;
        this.release();
    }

    private boolean available() {
        return !this.closed && !this.releasing && this.available.getAsBoolean();
    }

    public static final class Slot<T extends AutoCloseable> {
        private final CloseableResources owner;
        private final Supplier<T> factory;
        private @Nullable T value;
        private long generation;
        private boolean creating;
        private boolean releasing;

        private Slot(CloseableResources owner, Supplier<T> factory) {
            this.owner = owner;
            this.factory = factory;
        }

        // Acquire only in the owner's active phase; callers must not retain a resource across release().
        public @Nullable T get() {
            this.owner.checkAccess.run();
            if (this.creating || this.releasing || !this.owner.available()) return null;
            if (this.value == null) {
                this.creating = true;
                long generation = this.generation;
                try {
                    T created = Objects.requireNonNull(this.factory.get());
                    if (generation != this.generation || !this.owner.available()) {
                        this.discard(created);
                        return null;
                    }
                    this.value = created;
                } finally {
                    this.creating = false;
                }
            }
            return this.value;
        }

        public @Nullable T peek() {
            this.owner.checkAccess.run();
            return this.releasing || !this.owner.available() ? null : this.value;
        }

        public void release() {
            this.owner.checkAccess.run();
            this.generation++;
            T previous = this.value;
            this.value = null;
            if (previous == null) return;
            this.discard(previous);
        }

        private void discard(T previous) {
            this.releasing = true;
            try {
                previous.close();
            } catch (Exception exception) {
                this.owner.closeFailure.accept(exception);
            } finally {
                this.releasing = false;
            }
        }
    }
}
