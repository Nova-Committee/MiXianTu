package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.util.dispatcher.TickDispatcher;
import com.iafenvoy.mxt.util.dispatcher.TickExecutionLimit;
import com.iafenvoy.mxt.util.dispatcher.TickInterval;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public final class TickDispatcherVerifyProbes {
    private TickDispatcherVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/dispatcher/deadlines", TickDispatcherVerifyProbes::deadlines),
                Probe.of("verify/dispatcher/recurrence", TickDispatcherVerifyProbes::recurrence),
                Probe.of("verify/dispatcher/random", TickDispatcherVerifyProbes::random),
                Probe.of("verify/dispatcher/limits", TickDispatcherVerifyProbes::limits),
                Probe.of("verify/dispatcher/adaptive", TickDispatcherVerifyProbes::adaptive),
                Probe.of("verify/dispatcher/cancellation", TickDispatcherVerifyProbes::cancellation),
                Probe.of("verify/dispatcher/callbacks", TickDispatcherVerifyProbes::callbacks),
                Probe.of("verify/dispatcher/failures", TickDispatcherVerifyProbes::failures),
                Probe.of("verify/dispatcher/clock", TickDispatcherVerifyProbes::clock),
                Probe.of("verify/dispatcher/advance", TickDispatcherVerifyProbes::advance),
                Probe.of("verify/dispatcher/bounds", TickDispatcherVerifyProbes::bounds),
                Probe.of("verify/dispatcher/thread", TickDispatcherVerifyProbes::thread),
                Probe.of("verify/dispatcher/model", TickDispatcherVerifyProbes::model)
        );
    }

    private static void deadlines(ProbeContext context) {
        TickDispatcher<String> dispatcher = new TickDispatcher<>(100L, 1L);
        List<String> executed = new ArrayList<>();
        context.expect("first registration succeeds", dispatcher.scheduleOnce("a", 5L, () -> executed.add("a")), true);
        context.expect("a duplicate never replaces its callback", dispatcher.scheduleOnce("a", 0L, () -> executed.add("wrong")), false);
        dispatcher.scheduleOnce("b", 5L, () -> executed.add("b"));
        dispatcher.scheduleOnce("earlier", 2L, () -> executed.add("earlier"));
        context.expect("earliest task is visible", dispatcher.nextExecution(), OptionalLong.of(102L));
        context.expect("nothing runs early", dispatcher.tick(101L).executed(), 0);
        context.expect("one task reaches its deadline", dispatcher.tick(102L).executed(), 1);
        context.expect("completed one-shots are removed", dispatcher.contains("earlier"), false);
        context.expect("two tied tasks execute", dispatcher.tick(105L).executed(), 2);
        context.expect("deadline and registration order are stable", executed, List.of("earlier", "a", "b"));
        context.expect("the queue is empty", dispatcher.nextExecution(), OptionalLong.empty());
        context.expect("there are no registered tasks", dispatcher.size(), 0);
    }

    private static void recurrence(ProbeContext context) {
        TickDispatcher<String> dispatcher = new TickDispatcher<>(0L, 2L);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.schedule("repeat", TickInterval.fixed(10), calls::incrementAndGet);
        context.expect("recurrence waits for its first interval", dispatcher.tick(9L).executed(), 0);
        context.expect("first deadline executes", dispatcher.tick(10L).executed(), 1);
        context.expect("the next interval starts at execution", dispatcher.nextExecution("repeat"), OptionalLong.of(20L));
        context.expect("a large jump executes once", dispatcher.tick(100L).executed(), 1);
        context.expect("missed periods are not replayed", calls.get(), 2);
        context.expect("the jump starts a fresh interval", dispatcher.nextExecution("repeat"), OptionalLong.of(110L));
        context.expect("same tick cannot execute again", dispatcher.tick(100L).executed(), 0);
        dispatcher.reschedule("repeat", 0L);
        context.expect("rescheduling cannot bypass the tick gate", dispatcher.tick(100L).executed(), 0);
        dispatcher.tick(101L);
        context.expect("explicit delay preserves the recurring interval", dispatcher.nextExecution("repeat"), OptionalLong.of(111L));
        dispatcher.reschedule("repeat", TickInterval.fixed(3));
        context.expect("changing interval rebuilds the deadline", dispatcher.nextExecution("repeat"), OptionalLong.of(104L));
        dispatcher.tick(104L);
        context.expect("the new interval is used again", dispatcher.nextExecution("repeat"), OptionalLong.of(107L));
    }

    private static void random(ProbeContext context) {
        TickDispatcher<Integer> first = new TickDispatcher<>(0L, 97L);
        TickDispatcher<Integer> second = new TickDispatcher<>(0L, 97L);
        TickInterval interval = TickInterval.random(3, 7);
        first.schedule(1, interval, () -> {
        });
        second.schedule(1, interval, () -> {
        });
        Set<Long> delays = new HashSet<>();
        boolean withinRange = true, deterministic = true;
        long previous = 0L;
        for (int i = 0; i < 200; i++) {
            long due = first.nextExecution(1).orElseThrow();
            long delay = due - previous;
            withinRange &= delay >= 3L && delay <= 7L;
            deterministic &= second.nextExecution(1).orElseThrow() == due;
            delays.add(delay);
            first.tick(due);
            second.tick(due);
            previous = due;
        }
        context.expect("every initial and recurring interval is within both inclusive endpoints", withinRange, true);
        context.expect("an independent equal seed produces equal deadlines", deterministic, true);
        context.expect("the interval is redrawn rather than fixed at registration", delays.size() > 1, true);
        context.expect("all values in the small range are reached", delays, Set.of(3L, 4L, 5L, 6L, 7L));
        first.schedule(1, interval, () -> {
        });
        long next = first.nextExecution(1).orElseThrow();
        first.tick(next);
        second.tick(next);
        context.expect("duplicate registration does not consume a random interval", first.nextExecution(1), second.nextExecution(1));
        dispatcherDelay(context, new TickInterval(Integer.MAX_VALUE, Integer.MAX_VALUE), Integer.MAX_VALUE);
    }

    private static void dispatcherDelay(ProbeContext context, TickInterval interval, long expected) {
        TickDispatcher<Integer> dispatcher = new TickDispatcher<>(0L, 1L);
        dispatcher.schedule(1, interval, () -> {
        });
        context.expect("the largest interval remains representable", dispatcher.nextExecution(1), OptionalLong.of(expected));
    }

    private static void limits(ProbeContext context) {
        TickDispatcher<Integer> dispatcher = new TickDispatcher<>(0L, 3L, TickExecutionLimit.fixed(40));
        List<Integer> executed = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            int id = i;
            dispatcher.scheduleOnce(id, 0L, () -> executed.add(id));
        }
        TickDispatcher.TickReport<Integer> first = dispatcher.tick(0L);
        context.expect("the first tick is capped", first.executed(), 40);
        context.expect("the backlog is visible", first.hasDueTasks(), true);
        context.expect("same-tick work is not overdue yet", first.oldestOverdueTicks(), 0L);
        context.expect("repeated dispatch cannot reset the cap", dispatcher.tick(0L).executed(), 0);
        TickDispatcher.TickReport<Integer> second = dispatcher.tick(1L);
        context.expect("the next tick drains another capped batch", second.executed(), 40);
        context.expect("the oldest deadline reports real lateness", second.oldestOverdueTicks(), 1L);
        context.expect("the last tick drains the remainder", dispatcher.tick(2L).executed(), 25);
        context.expect("every task executes once in order", executed, IntStream.range(0, 105).boxed().toList());
        context.expect("an old report is a stable snapshot", first.scheduled(), 65);

        for (int i = 0; i < 100; i++)
            dispatcher.scheduleOnce(i, 0L, () -> {
            });
        dispatcher.setLimitEnabled(false);
        context.expect("turning the limit off drains the due snapshot", dispatcher.tick(3L).executed(), 100);
        dispatcher.setLimitEnabled(true);
        for (int i = 0; i < 50; i++)
            dispatcher.scheduleOnce(i, 0L, () -> {
            });
        context.expect("turning it on restores the configured cap", dispatcher.tick(4L).executed(), 40);
        dispatcher.setExecutionLimit(TickExecutionLimit.fixed(2));
        context.expect("new policy also applies on the next tick", dispatcher.tick(5L).executed(), 2);
    }

    private static void adaptive(ProbeContext context) {
        TickExecutionLimit limit = TickExecutionLimit.adaptive(8, 600);
        context.expect("small populations use the minimum", limit.maximum(1), 8);
        context.expect("the design population has forty attempts", limit.maximum(24000), 40);
        context.expect("division rounds up", limit.maximum(24001), 41);
        context.expect("large population arithmetic does not overflow", TickExecutionLimit.adaptive(1, 2).maximum(Integer.MAX_VALUE), 1073741824);
        TickDispatcher<Integer> dispatcher = new TickDispatcher<>(0L, 4L, TickExecutionLimit.adaptive(1, 3));
        for (int i = 0; i < 10; i++)
            dispatcher.scheduleOnce(i, 0L, () -> {
            });
        context.expect("ten scheduled tasks allow four attempts", dispatcher.tick(0L).executed(), 4);
        context.expect("six remaining tasks allow two attempts", dispatcher.tick(1L).executed(), 2);
        dispatcher.cancel(9);
        context.expect("unloading tasks immediately reduces population", dispatcher.tick(2L).executed(), 1);
    }

    private static void cancellation(ProbeContext context) {
        TickDispatcher<Integer> dispatcher = new TickDispatcher<>(0L, 5L);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.schedule(1, TickInterval.fixed(2), calls::incrementAndGet);
        context.expect("cancelling an unknown task is harmless", dispatcher.cancel(2), false);
        context.expect("a registered task can be cancelled", dispatcher.cancel(1), true);
        context.expect("the cancelled deadline leaves the index", dispatcher.nextExecution(), OptionalLong.empty());
        dispatcher.tick(5L);
        context.expect("cancelled callbacks never run", calls.get(), 0);
        dispatcher.scheduleOnce(1, 5L, calls::incrementAndGet);
        dispatcher.reschedule(1, 1L);
        context.expect("reschedule removes the old deadline", dispatcher.nextExecution(1), OptionalLong.of(6L));
        dispatcher.tick(6L);
        dispatcher.tick(10L);
        context.expect("a rescheduled one-shot executes once", calls.get(), 1);
        context.expect("completed keys can be registered again", dispatcher.scheduleOnce(1, 0L, calls::incrementAndGet), true);
        dispatcher.clear();
        context.expect("clear releases both indices", dispatcher.size(), 0);
        context.expect("clear leaves no deadline", dispatcher.nextExecution(), OptionalLong.empty());
        context.expect("missing tasks cannot be rescheduled", dispatcher.reschedule(1, 1L), false);
    }

    private static void callbacks(ProbeContext context) {
        TickDispatcher<String> dispatcher = new TickDispatcher<>(0L, 6L);
        List<String> calls = new ArrayList<>();
        dispatcher.scheduleOnce("first", 0L, () -> {
            calls.add("first");
            dispatcher.cancel("cancelled");
            dispatcher.reschedule("moved", 10L);
            dispatcher.scheduleOnce("new", 0L, () -> calls.add("new"));
        });
        dispatcher.scheduleOnce("cancelled", 0L, () -> calls.add("wrong"));
        dispatcher.scheduleOnce("moved", 0L, () -> calls.add("moved"));
        context.expect("cancel and reschedule affect the detached batch", dispatcher.tick(0L).executed(), 1);
        context.expect("new due tasks wait for the next tick", calls, List.of("first"));
        dispatcher.tick(1L);
        dispatcher.tick(10L);
        context.expect("the replacement and new callback both run later", calls, List.of("first", "new", "moved"));

        dispatcher.schedule("self", TickInterval.fixed(1), () -> dispatcher.cancel("self"));
        dispatcher.tick(11L);
        context.expect("a self-cancelled recurrence is not resurrected", dispatcher.contains("self"), false);
        dispatcher.schedule("self", TickInterval.fixed(1), () -> dispatcher.reschedule("self", 7L));
        dispatcher.tick(12L);
        context.expect("explicit self-reschedule wins over recurrence", dispatcher.nextExecution("self"), OptionalLong.of(19L));
        dispatcher.clear();
        dispatcher.scheduleOnce("clear", 0L, () -> {
            dispatcher.clear();
            dispatcher.scheduleOnce("tail", 0L, () -> calls.add("tail"));
        });
        dispatcher.scheduleOnce("obsolete", 0L, () -> calls.add("wrong"));
        context.expect("clear cancels the remaining selected tasks", dispatcher.tick(13L).executed(), 1);
        context.expect("only the callback-created task survives clear", dispatcher.size(), 1);
        dispatcher.tick(14L);
        context.expect("the surviving task executes later", calls.getLast(), "tail");

        dispatcher.scheduleOnce("replace", 0L, () -> {
            dispatcher.cancel("same-key");
            dispatcher.scheduleOnce("same-key", 0L, () -> calls.add("replacement"));
        });
        dispatcher.scheduleOnce("same-key", 0L, () -> calls.add("wrong"));
        context.expect("a reused key never executes its detached predecessor", dispatcher.tick(15L).executed(), 1);
        context.expect("the replacement survives completion of the old batch", dispatcher.size(), 1);
        dispatcher.tick(16L);
        context.expect("the replacement executes on the following tick", calls.getLast(), "replacement");

        AtomicInteger spawned = new AtomicInteger();
        Runnable[] producer = new Runnable[1];
        producer[0] = () -> dispatcher.scheduleOnce("spawn-" + spawned.incrementAndGet(), 0L, producer[0]);
        dispatcher.scheduleOnce("spawn", 0L, producer[0]);
        context.expect("unlimited mode bounds callback-created zero-delay chains", dispatcher.tick(17L).executed(), 1);
        context.expect("a new task remains queued", dispatcher.size(), 1);
    }

    private static void failures(ProbeContext context) {
        TickDispatcher<String> dispatcher = new TickDispatcher<>(0L, 7L);
        AtomicInteger healthy = new AtomicInteger();
        dispatcher.schedule("broken", TickInterval.fixed(1), () -> {
            throw new IllegalStateException("expected probe failure");
        });
        dispatcher.scheduleOnce("healthy", 1L, healthy::incrementAndGet);
        TickDispatcher.TickReport<String> report = dispatcher.tick(1L);
        context.expect("failed callbacks consume budget", report.executed(), 2);
        context.expect("one failure is returned to the owner", report.failures().size(), 1);
        context.expect("the failure retains its key", report.failures().getFirst().key(), "broken");
        context.expect("another callback survives a runtime failure", healthy.get(), 1);
        context.expect("recurring failures are still scheduled for a later tick", dispatcher.nextExecution("broken"), OptionalLong.of(2L));
        expectThrows(context, "reports expose immutable failure lists", UnsupportedOperationException.class, () -> report.failures().clear());
        dispatcher.clear();
        dispatcher.scheduleOnce("recursive", 0L, () -> dispatcher.tick(100L));
        context.expect("recursive dispatch is reported as one task failure", dispatcher.tick(2L).failures().size(), 1);
        context.expect("recursive calls cannot change the outer clock", dispatcher.currentTick(), 2L);

        TickDispatcher<String> nested = new TickDispatcher<>(0L, 70L);
        nested.scheduleOnce("nested", 0L, healthy::incrementAndGet);
        dispatcher.scheduleOnce("outer", 0L, () -> nested.tick(2L));
        context.expect("independent dispatchers can nest on the same thread", dispatcher.tick(3L).failures().size(), 0);
        context.expect("the nested dispatcher executes its own work", healthy.get(), 2);

        dispatcher.scheduleOnce("fatal", 0L, () -> {
            throw new AssertionError("expected fatal probe failure");
        });
        dispatcher.scheduleOnce("survivor", 0L, healthy::incrementAndGet);
        expectThrows(context, "fatal errors propagate", AssertionError.class, () -> dispatcher.tick(4L));
        context.expect("fatal one-shots are retired", dispatcher.contains("fatal"), false);
        context.expect("the detached unexecuted task returns to the queue", dispatcher.nextExecution("survivor"), OptionalLong.of(3L));
        context.expect("a fatal error cannot reset the current tick budget", dispatcher.tick(4L).executed(), 0);
        context.expect("the restored task executes next tick", dispatcher.tick(5L).executed(), 1);
    }

    private static void clock(ProbeContext context) {
        TickDispatcher<String> dispatcher = new TickDispatcher<>(100L, 8L, TickExecutionLimit.fixed(1));
        AtomicInteger calls = new AtomicInteger();
        dispatcher.scheduleOnce("future", 20L, calls::incrementAndGet);
        dispatcher.tick(110L);
        dispatcher.tick(10L);
        context.expect("rewind preserves the remaining delay", dispatcher.nextExecution("future"), OptionalLong.of(20L));
        context.expect("rewind does not execute future work", calls.get(), 0);
        dispatcher.tick(20L);
        context.expect("the rebased deadline executes once", calls.get(), 1);
        dispatcher.scheduleOnce("due-a", 0L, calls::incrementAndGet);
        dispatcher.scheduleOnce("due-b", 0L, calls::incrementAndGet);
        dispatcher.tick(21L);
        dispatcher.tick(0L);
        context.expect("overdue work remains due after rewind", calls.get(), 3);
        context.expect("rewind cannot revive completed work", dispatcher.size(), 0);
    }

    private static void bounds(ProbeContext context) {
        expectThrows(context, "zero recurring intervals are refused", IllegalArgumentException.class, () -> TickInterval.fixed(0));
        expectThrows(context, "reversed intervals are refused", IllegalArgumentException.class, () -> TickInterval.random(3, 2));
        expectThrows(context, "negative initial ticks are refused", IllegalArgumentException.class, () -> new TickDispatcher<>(-1L, 0L));
        expectThrows(context, "zero limits are refused", IllegalArgumentException.class, () -> TickExecutionLimit.fixed(0));
        expectThrows(context, "adaptive spread must be positive", IllegalArgumentException.class, () -> TickExecutionLimit.adaptive(1, 0));
        TickDispatcher<String> dispatcher = new TickDispatcher<>(Long.MAX_VALUE - 1L, 9L);
        expectThrows(context, "negative delays are refused", IllegalArgumentException.class, () -> dispatcher.scheduleOnce("bad", -1L, () -> {
        }));
        expectThrows(context, "negative tick calls are refused", IllegalArgumentException.class, () -> dispatcher.tick(-1L));
        context.expect("invalid operations leave no tasks", dispatcher.size(), 0);
        dispatcher.schedule("edge", TickInterval.fixed(10), () -> {
        });
        dispatcher.scheduleOnce("huge", Long.MAX_VALUE, () -> {
        });
        context.expect("overflowing deadlines saturate", dispatcher.nextExecution("edge"), OptionalLong.of(Long.MAX_VALUE));
        context.expect("nothing becomes prematurely due on overflow", dispatcher.tick(Long.MAX_VALUE - 1L).executed(), 0);
        context.expect("the final representable tick executes due work", dispatcher.tick(Long.MAX_VALUE).executed(), 2);
        context.expect("recurrence retires when no later tick exists", dispatcher.size(), 0);
    }

    private static void advance(ProbeContext context) {
        TickDispatcher<String> dispatcher = new TickDispatcher<>(0L, 81L, TickExecutionLimit.fixed(1));
        AtomicInteger calls = new AtomicInteger();
        dispatcher.scheduleOnce("due", 1L, calls::incrementAndGet);
        dispatcher.advanceTo(100L);
        context.expect("clock synchronization does not dispatch overdue work", calls.get(), 0);
        dispatcher.schedule("new", TickInterval.fixed(10), calls::incrementAndGet);
        context.expect("new deadlines use the synchronized time", dispatcher.nextExecution("new"), OptionalLong.of(110L));
        context.expect("synchronization leaves the tick budget available", dispatcher.tick(100L).executed(), 1);
        dispatcher.scheduleOnce("tail", 0L, calls::incrementAndGet);
        dispatcher.advanceTo(100L);
        context.expect("same-time synchronization cannot replenish the budget", dispatcher.tick(100L).executed(), 0);
        dispatcher.advanceTo(10L);
        context.expect("clock synchronization rebases remaining delays on rewind", dispatcher.nextExecution("new"), OptionalLong.of(20L));
        dispatcher.tick(10L);
        dispatcher.scheduleOnce("callback", 0L, () -> dispatcher.advanceTo(200L));
        context.expect("callbacks cannot move their dispatcher's clock", dispatcher.tick(11L).failures().size(), 1);
        context.expect("the rejected advance leaves the active clock intact", dispatcher.currentTick(), 11L);
    }

    private static void thread(ProbeContext context) {
        TickDispatcher<Integer> dispatcher = new TickDispatcher<>(0L, 10L);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                dispatcher.tick(1L);
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        try {
            worker.join(5000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            context.fail("dispatcher thread probe was interrupted");
        }
        context.expect("cross-thread access fails before mutating the clock", failure.get() instanceof IllegalStateException, true);
        context.expect("the owner clock is unchanged", dispatcher.currentTick(), 0L);
        context.expect("the owner can still dispatch", dispatcher.tick(1L).executed(), 0);
    }

    private static void model(ProbeContext context) {
        TickDispatcher<Integer> dispatcher = new TickDispatcher<>(0L, 11L, TickExecutionLimit.fixed(7));
        Random random = new Random(11L);
        List<Integer> calls = new ArrayList<>();
        Map<Integer, ModelTask> expected = new HashMap<>();
        long sequence = 0L;
        boolean equivalent = true;
        for (long tick = 0; tick < 300L; tick++) {
            long base = dispatcher.currentTick();
            for (int mutation = 0; mutation < 10; mutation++) {
                int key = random.nextInt(80);
                long delay = random.nextInt(20);
                switch (random.nextInt(3)) {
                    case 0 -> {
                        boolean registered = dispatcher.scheduleOnce(key, delay, () -> calls.add(key));
                        equivalent &= registered == !expected.containsKey(key);
                        if (registered) expected.put(key, new ModelTask(key, base + delay, ++sequence));
                    }
                    case 1 -> {
                        boolean changed = dispatcher.reschedule(key, delay);
                        equivalent &= changed == expected.containsKey(key);
                        if (changed) expected.put(key, new ModelTask(key, base + delay, ++sequence));
                    }
                    default -> equivalent &= dispatcher.cancel(key) == (expected.remove(key) != null);
                }
            }
            long now = tick;
            List<Integer> due = expected.values().stream().filter(task -> task.due <= now)
                    .sorted(Comparator.comparingLong(ModelTask::due).thenComparingLong(ModelTask::sequence))
                    .limit(7L).map(ModelTask::key).toList();
            calls.clear();
            TickDispatcher.TickReport<Integer> report = dispatcher.tick(tick);
            equivalent &= calls.equals(due) && report.executed() == due.size();
            due.forEach(expected::remove);
            equivalent &= report.scheduled() == expected.size();
            OptionalLong first = expected.values().stream().mapToLong(ModelTask::due).min();
            equivalent &= dispatcher.nextExecution().equals(first);
        }
        context.expect("300 ticks and 3000 mutations match the sorted reference model", equivalent, true);
    }

    private static void expectThrows(ProbeContext context, String name, Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
            context.fail(name + ": no exception was thrown");
        } catch (Throwable exception) {
            if (!expected.isInstance(exception)) context.fail(name + ": unexpected " + exception);
        }
    }

    private record ModelTask(int key, long due, long sequence) {
    }
}
