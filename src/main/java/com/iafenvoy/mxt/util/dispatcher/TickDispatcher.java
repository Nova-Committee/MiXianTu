package com.iafenvoy.mxt.util.dispatcher;

import org.jetbrains.annotations.NotNull;

import java.util.*;

// Confined to its creating thread. Keys must keep stable equals/hashCode until cancelled or completed.
public final class TickDispatcher<K> {
    private static final ThreadLocal<Set<TickDispatcher<?>>> DISPATCHING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Thread owner = Thread.currentThread();
    private final SplittableRandom random;
    private final Map<K, Task<K>> tasks = new HashMap<>();
    private final NavigableSet<Task<K>> queue = new TreeSet<>(Comparator.<Task<K>>comparingLong(task -> task.dueTick).thenComparingLong(task -> task.sequence));
    private TickExecutionLimit executionLimit;
    private long currentTick;
    private long sequence;
    private boolean dispatched;

    public TickDispatcher(long initialTick, long seed) {
        this(initialTick, seed, TickExecutionLimit.unlimited());
    }

    public TickDispatcher(long initialTick, long seed, @NotNull TickExecutionLimit executionLimit) {
        requireTick(initialTick);
        this.currentTick = initialTick;
        this.random = new SplittableRandom(seed);
        this.executionLimit = executionLimit;
    }

    public boolean scheduleOnce(@NotNull K key, long delayTicks, @NotNull Runnable action) {
        this.checkThread();
        requireDelay(delayTicks);
        return this.schedule(key, delayTicks, null, action);
    }

    public boolean schedule(@NotNull K key, @NotNull TickInterval interval, @NotNull Runnable action) {
        this.checkThread();
        if (this.tasks.containsKey(key)) return false;
        return this.schedule(key, interval.nextDelay(this.random), interval, action);
    }

    private boolean schedule(@NotNull K key, long delay, TickInterval interval, @NotNull Runnable action) {
        if (this.tasks.containsKey(key)) return false;
        this.put(new Task<>(key, this.nextSequence(), deadline(this.currentTick, delay), interval, action));
        return true;
    }

    public boolean reschedule(@NotNull K key, long delayTicks) {
        this.checkThread();
        requireDelay(delayTicks);
        Task<K> task = this.tasks.get(key);
        return task != null && this.replace(task, delayTicks, task.interval);
    }

    // Also changes a one-shot into a recurring task; the first new deadline is relative to the current tick.
    public boolean reschedule(@NotNull K key, @NotNull TickInterval interval) {
        this.checkThread();
        Task<K> task = this.tasks.get(key);
        return task != null && this.replace(task, interval.nextDelay(this.random), interval);
    }

    private boolean replace(Task<K> task, long delay, TickInterval interval) {
        Task<K> replacement = new Task<>(task.key, this.nextSequence(), deadline(this.currentTick, delay), interval, task.action);
        this.queue.remove(task);
        this.put(replacement);
        return true;
    }

    public boolean cancel(@NotNull K key) {
        this.checkThread();
        Task<K> task = this.tasks.remove(key);
        if (task == null) return false;
        this.queue.remove(task);
        return true;
    }

    public void clear() {
        this.checkThread();
        this.tasks.clear();
        this.queue.clear();
    }

    public int size() {
        this.checkThread();
        return this.tasks.size();
    }

    public boolean contains(@NotNull K key) {
        this.checkThread();
        return this.tasks.containsKey(key);
    }

    public OptionalLong nextExecution(@NotNull K key) {
        this.checkThread();
        Task<K> task = this.tasks.get(key);
        return task == null ? OptionalLong.empty() : OptionalLong.of(task.dueTick);
    }

    public OptionalLong nextExecution() {
        this.checkThread();
        return this.queue.isEmpty() ? OptionalLong.empty() : OptionalLong.of(this.queue.first().dueTick);
    }

    public long currentTick() {
        this.checkThread();
        return this.currentTick;
    }

    public TickExecutionLimit executionLimit() {
        this.checkThread();
        return this.executionLimit;
    }

    public void setExecutionLimit(@NotNull TickExecutionLimit limit) {
        this.checkThread();
        this.executionLimit = limit;
    }

    public void setLimitEnabled(boolean enabled) {
        this.setExecutionLimit(this.executionLimit().withEnabled(enabled));
    }

    // Synchronizes relative registration without dispatching; it cannot change the clock inside a callback.
    public void advanceTo(long now) {
        this.checkThread();
        requireTick(now);
        Set<TickDispatcher<?>> active = DISPATCHING.get();
        try {
            if (active.contains(this)) throw new IllegalStateException("A running dispatcher cannot advance its clock");
            this.updateTick(now);
        } finally {
            if (active.isEmpty()) DISPATCHING.remove();
        }
    }

    // One dispatch per tick; callback-created or rescheduled tasks wait for the next dispatch, even without a limit.
    public TickReport<K> tick(long now) {
        this.checkThread();
        requireTick(now);
        Set<TickDispatcher<?>> active = DISPATCHING.get();
        if (!active.add(this)) throw new IllegalStateException("A dispatcher cannot recursively tick itself");
        List<Task<K>> ready = new ArrayList<>();
        try {
            this.updateTick(now);
            if (this.dispatched) return this.report(0, List.of());
            this.dispatched = true;
            int maximum = this.executionLimit.maximum(this.tasks.size());
            while (ready.size() < maximum && !this.queue.isEmpty() && this.queue.first().dueTick <= now)
                ready.add(this.queue.pollFirst());
            int executed = 0;
            List<TaskFailure<K>> failures = new ArrayList<>();
            for (Task<K> task : ready) {
                if (this.tasks.get(task.key) != task) continue;
                executed++;
                try {
                    task.action.run();
                } catch (RuntimeException exception) {
                    failures.add(new TaskFailure<>(task.key, exception));
                } finally {
                    this.complete(task);
                }
            }
            return this.report(executed, failures);
        } finally {
            // An Error propagates, but unexecuted tasks from the detached batch must remain scheduled.
            for (Task<K> task : ready)
                if (this.tasks.get(task.key) == task) this.queue.add(task);
            active.remove(this);
            if (active.isEmpty()) DISPATCHING.remove();
        }
    }

    private void complete(Task<K> task) {
        if (this.tasks.get(task.key) != task) return;
        if (task.interval == null || this.currentTick == Long.MAX_VALUE) {
            this.tasks.remove(task.key);
            return;
        }
        this.replace(task, task.interval.nextDelay(this.random), task.interval);
    }

    private void updateTick(long now) {
        if (now == this.currentTick) return;
        if (now < this.currentTick) this.rebase(now);
        this.currentTick = now;
        this.dispatched = false;
    }

    private void rebase(long now) {
        this.queue.clear();
        for (Task<K> task : this.tasks.values()) {
            long remaining = Math.max(0L, task.dueTick - this.currentTick);
            task.dueTick = deadline(now, remaining);
            this.queue.add(task);
        }
    }

    private void put(Task<K> task) {
        this.tasks.put(task.key, task);
        this.queue.add(task);
    }

    private long nextSequence() {
        return this.sequence = Math.incrementExact(this.sequence);
    }

    private TickReport<K> report(int executed, List<TaskFailure<K>> failures) {
        return new TickReport<>(this.currentTick, executed, this.tasks.size(), this.nextExecution(), failures);
    }

    private void checkThread() {
        if (Thread.currentThread() != this.owner)
            throw new IllegalStateException("A dispatcher belongs to its creating thread");
    }

    private static void requireTick(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("Tick cannot be negative");
    }

    private static void requireDelay(long delay) {
        if (delay < 0L) throw new IllegalArgumentException("Delay cannot be negative");
    }

    private static long deadline(long now, long delay) {
        return delay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + delay;
    }

    public record TickReport<K>(long tick, int executed, int scheduled, OptionalLong nextDueTick,
                                List<TaskFailure<K>> failures) {
        public TickReport {
            failures = List.copyOf(failures);
        }

        public boolean hasDueTasks() {
            return this.nextDueTick.isPresent() && this.nextDueTick.getAsLong() <= this.tick;
        }

        public long oldestOverdueTicks() {
            return this.nextDueTick.isEmpty() ? 0L : Math.max(0L, this.tick - this.nextDueTick.getAsLong());
        }
    }

    public record TaskFailure<K>(K key, RuntimeException exception) {
    }

    private static final class Task<K> {
        private final K key;
        private final long sequence;
        private long dueTick;
        private final TickInterval interval;
        private final Runnable action;

        private Task(K key, long sequence, long dueTick, TickInterval interval, Runnable action) {
            this.key = key;
            this.sequence = sequence;
            this.dueTick = dueTick;
            this.interval = interval;
            this.action = action;
        }
    }
}
