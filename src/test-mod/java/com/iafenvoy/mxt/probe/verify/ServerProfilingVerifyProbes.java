package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.command.server.DebugCommand;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService.Denomination;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ProfilerWindowSnapshot;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.iafenvoy.mxt.util.profiling.ServerProfiling.Source;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerProfilingVerifyProbes {
    private ServerProfilingVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/profiling/server_defaults", ServerProfilingVerifyProbes::defaults),
                Probe.of("verify/profiling/server_sources", ServerProfilingVerifyProbes::sources),
                Probe.of("verify/profiling/server_lifecycle", ServerProfilingVerifyProbes::lifecycle),
                Probe.of("verify/profiling/server_tick_boundary", ServerProfilingVerifyProbes::tickBoundary),
                Probe.of("verify/profiling/server_commands", ServerProfilingVerifyProbes::commands),
                Probe.of("verify/profiling/server_status", ServerProfilingVerifyProbes::status)
        );
    }

    private static void defaults(ProbeContext context) {
        boolean previous = ServerProfiling.enabled(context.server());
        expect(context, "server startup follows the shared dev-mode switch", previous, MiXianTu.isDevMode());
        expect(context, "automatic startup still allows manual disable", ServerProfiling.setEnabled(context.server(), false), true);
        context.after(2L, () -> {
            try {
                expect(context, "ticks do not override manual disable", ServerProfiling.enabled(context.server()), false);
            } finally {
                ServerProfiling.setEnabled(context.server(), previous);
            }
            context.done();
        });
    }

    private static void sources(ProbeContext context) {
        boolean previous = ServerProfiling.enabled(context.server());
        ServerProfiling.setEnabled(context.server(), false);
        expect(context, "disabled server has no samples", snapshot(context).producers().size(), 0);
        expect(context, "the server owns its profiling switch", ServerProfiling.setEnabled(context.server(), true), true);
        try {
            try (ProfilerScope _ = ServerProfiling.scope(Source.ECONOMY_CHANGE);
                 ProfilerScope _ = ServerProfiling.scope(Source.ECONOMY_CHANGE)) {
                expect(context, "profiled change keeps its exact result", change(), 3L);
            }
            expect(context, "a second change keeps its result", change(), 3L);
            expect(context, "profiled empty payment still succeeds",
                    CostPayment.pay(List.of(), CostContext.of(context.player(), CostOrigins.ABILITY)).isSuccess(), true);
            AtomicLong workerResult = new AtomicLong(-1L);
            Thread worker = Thread.ofPlatform().start(() -> workerResult.set(change()));
            try {
                worker.join(5_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            expect(context, "worker business still executes", workerResult.get(), 3L);
        } catch (RuntimeException | Error exception) {
            ServerProfiling.setEnabled(context.server(), previous);
            throw exception;
        }
        context.after(2L, () -> {
            try {
                ProfilerWindowSnapshot snapshot = snapshot(context);
                expect(context, "only the server thread produces samples", snapshot.producers().size(), 1);
                ProfilerWindowSnapshot.Producer producer = snapshot.producers().getFirst();
                expect(context, "nested and worker calls add no duplicate change hit", hits(snapshot, producer, 0, Source.ECONOMY_CHANGE), 2L);
                expect(context, "the real payment entry is sampled", hits(snapshot, producer, 0, Source.COST) > 0L, true);
                expect(context, "child durations are included by their module",
                        nanos(snapshot, producer, 0, Source.ECONOMY) >= nanos(snapshot, producer, 0, Source.ECONOMY_CHANGE), true);
                expect(context, "the normal server tick is complete", producer.complete(0), true);
            } finally {
                ServerProfiling.setEnabled(context.server(), previous);
            }
            context.done();
        });
    }

    private static void lifecycle(ProbeContext context) {
        boolean previous = ServerProfiling.enabled(context.server());
        ServerProfiling.setEnabled(context.server(), false);
        ServerProfiling.setEnabled(context.server(), true);
        ProfilerScope old = ServerProfiling.scope(Source.ECONOMY_CHANGE);
        ServerProfiling.setEnabled(context.server(), false);
        expect(context, "off discards the server session", snapshot(context).producers().size(), 0);
        ServerProfiling.setEnabled(context.server(), true);
        try (ProfilerScope _ = ServerProfiling.scope(Source.ECONOMY_CHANGE)) {
            old.close();
            expect(context, "stale closes leave fresh work intact", change(), 3L);
        }
        try {
            try (ProfilerScope _ = ServerProfiling.scope(Source.ECONOMY_CHANGE)) {
                throw new IllegalStateException("Expected scope-unwinding probe");
            }
        } catch (IllegalStateException ignored) {
        }
        expect(context, "work after unwinding still executes", change(), 3L);
        context.after(2L, () -> {
            try {
                ProfilerWindowSnapshot snapshot = snapshot(context);
                ProfilerWindowSnapshot.Producer producer = snapshot.producers().getFirst();
                expect(context, "restart, duplicate close and exception preserve three outer calls",
                        hits(snapshot, producer, 0, Source.ECONOMY_CHANGE), 3L);
                expect(context, "properly unwound scopes keep the tick complete", producer.complete(0), true);
            } finally {
                ServerProfiling.setEnabled(context.server(), previous);
            }
            context.done();
        });
    }

    private static void tickBoundary(ProbeContext context) {
        boolean previous = ServerProfiling.enabled(context.server());
        ServerProfiling.setEnabled(context.server(), false);
        ServerProfiling.setEnabled(context.server(), true);
        ProfilerScope abandoned = ServerProfiling.scope(Source.ECONOMY_CHANGE);
        context.after(1L, () -> {
            try (ProfilerScope _ = ServerProfiling.scope(Source.ECONOMY_CHANGE)) {
                abandoned.close();
                expect(context, "a cross-tick close does not clear the fresh source", change(), 3L);
                abandoned.close();
            }
        });
        context.after(2L, () -> {
            try {
                ProfilerWindowSnapshot snapshot = snapshot(context);
                ProfilerWindowSnapshot.Producer producer = snapshot.producers().getFirst();
                expect(context, "the abandoned scope marks its tick incomplete", producer.complete(0), false);
                expect(context, "the next tick starts with clean guards", producer.complete(1), true);
                expect(context, "cross-tick closes cannot cause nested double counting",
                        hits(snapshot, producer, 1, Source.ECONOMY_CHANGE), 1L);
            } finally {
                ServerProfiling.setEnabled(context.server(), previous);
            }
            context.done();
        });
    }

    private static long change() {
        return CurrencyPaymentService.planCounts(List.of(new Denomination(Items.EMERALD, 2L)), 6L, 1).orElseThrow()[0];
    }

    private static void commands(ProbeContext context) {
        boolean previous = ServerProfiling.enabled(context.server());
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = context.server().getCommands().getDispatcher();
            CommandSourceStack source = context.server().createCommandSourceStack();
            expect(context, "the debug on command succeeds", dispatcher.execute("mxt debug on", source), 1);
            expect(context, "the command enables the server owner", ServerProfiling.enabled(context.server()), true);
            expect(context, "status accepts multiple fixed source ids",
                    dispatcher.execute("mxt debug status server/cost server/economy/change", source), 2);
            expect(context, "status rejects unknown source ids", dispatcher.execute("mxt debug status missing", source), 0);
            expect(context, "the debug off command succeeds", dispatcher.execute("mxt debug off", source), 1);
            expect(context, "the command disables the server owner", ServerProfiling.enabled(context.server()), false);
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException(exception);
        } finally {
            ServerProfiling.setEnabled(context.server(), previous);
        }
    }

    private static void status(ProbeContext context) {
        List<Component> messages = new ArrayList<>();
        CommandSourceStack source = context.server().createCommandSourceStack().withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(@NonNull Component message) {
                messages.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        });
        ProfilerWindowSnapshot window = ProfilerWindowSnapshot.restore(1L, 1L, 2L, List.of(Source.COST.id()),
                List.of(new ProfilerWindowSnapshot.ProducerData(1L, "Server thread", 0L,
                        new long[][]{{1_000_000L, 1L}, {10_000_000L, 2L}, {50_000_000L, 3L}}, new boolean[]{true, true, true}, 3L)));
        try {
            Method show = DebugCommand.class.getDeclaredMethod("show", CommandSourceStack.class, List.class, ProfilerWindowSnapshot.class);
            show.setAccessible(true);
            show.invoke(null, source, List.of(Source.COST), window);
            TranslatableContents header = (TranslatableContents) messages.get(0).getContents();
            TranslatableContents producer = (TranslatableContents) messages.get(1).getContents();
            TranslatableContents row = (TranslatableContents) messages.get(2).getContents();
            expect(context, "status retains the selected source id", row.getArgs()[0], Source.COST.id());
            ChatFormatting[] colors = {ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.RED,
                    ChatFormatting.YELLOW, ChatFormatting.RED, ChatFormatting.RED};
            for (int index = 0; index < colors.length; index++)
                expect(context, "timing argument " + index + " uses its duration color", ((Component) row.getArgs()[index + 1]).getStyle().getColor(), TextColor.fromLegacyFormat(colors[index]));
            expect(context, "dropped batches are red", ((Component) header.getArgs()[1]).getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED));
            expect(context, "discarded records are red", ((Component) producer.getArgs()[2]).getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED));

            messages.clear();
            ProfilerWindowSnapshot clean = ProfilerWindowSnapshot.restore(1L, 1L, 0L, List.of(Source.COST.id()),
                    List.of(new ProfilerWindowSnapshot.ProducerData(1L, "Server thread", 0L,
                            new long[][]{{0L, 0L}}, new boolean[]{true}, 0L)));
            show.invoke(null, source, List.of(Source.COST), clean);
            header = (TranslatableContents) messages.get(0).getContents();
            producer = (TranslatableContents) messages.get(1).getContents();
            expect(context, "zero dropped batches are green", ((Component) header.getArgs()[1]).getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.GREEN));
            expect(context, "zero discarded records are green", ((Component) producer.getArgs()[2]).getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.GREEN));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ProfilerWindowSnapshot snapshot(ProbeContext context) {
        try {
            return ServerProfiling.snapshot(context.server()).get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Server profiler snapshot did not finish", exception);
        }
    }

    private static long hits(ProfilerWindowSnapshot snapshot, ProfilerWindowSnapshot.Producer producer, int sample, Source source) {
        return producer.hits(sample, snapshot.sources().stream().filter(value -> value.id().equals(source.id())).findFirst().orElseThrow());
    }

    private static long nanos(ProfilerWindowSnapshot snapshot, ProfilerWindowSnapshot.Producer producer, int sample, Source source) {
        return producer.nanos(sample, snapshot.sources().stream().filter(value -> value.id().equals(source.id())).findFirst().orElseThrow());
    }

    private static void expect(ProbeContext context, String message, Object actual, Object expected) {
        context.expect(message, actual, expected);
        context.note(message + ": " + (Objects.equals(actual, expected) ? "OK" : "MISMATCH")
                + " actual=" + actual + " expected=" + expected);
    }
}
