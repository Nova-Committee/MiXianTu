package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.util.profiling.*;
import com.iafenvoy.mxt.util.profiling.ServerProfiling.Source;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DebugCommand {
    private DebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("debug").requires(source -> ProfilerSubscriptions.mayView(source.permissions()))
                .then(literal("gui").executes(ctx -> ProfilerSubscriptions.open(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(literal("on").executes(ctx -> toggle(ctx.getSource(), true)))
                .then(literal("off").executes(ctx -> toggle(ctx.getSource(), false)))
                .then(literal("status").executes(ctx -> status(ctx.getSource(), ""))
                        .then(argument("sources", StringArgumentType.greedyString())
                                .suggests((_, builder) -> {
                                    String remaining = builder.getRemaining();
                                    int offset = remaining.lastIndexOf(' ') + 1;
                                    return SharedSuggestionProvider.suggest(Arrays.stream(Source.values()).map(Source::id),
                                            builder.createOffset(builder.getStart() + offset));
                                })
                                .executes(ctx -> status(ctx.getSource(), StringArgumentType.getString(ctx, "sources")))));
    }

    private static int toggle(CommandSourceStack source, boolean enabled) {
        if (!ServerProfiling.setEnabled(source.getServer(), enabled)) {
            source.sendFailure(Component.translatable("command.mxt.debug.unavailable"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(enabled ? "command.mxt.debug.enabled" : "command.mxt.debug.disabled"), true);
        return 1;
    }

    private static int status(CommandSourceStack source, String arguments) {
        LinkedHashSet<Source> selected = new LinkedHashSet<>();
        if (arguments.isBlank()) selected.addAll(List.of(Source.values()));
        else for (String id : arguments.strip().split("\\s+")) {
            Source match = Arrays.stream(Source.values()).filter(value -> value.id().equals(id)).findFirst().orElse(null);
            if (match == null) {
                source.sendFailure(Component.translatable("command.mxt.debug.unknown_source", id));
                return 0;
            }
            selected.add(match);
        }
        if (!ServerProfiling.enabled(source.getServer())) {
            source.sendFailure(Component.translatable("command.mxt.debug.disabled"));
            return 0;
        }
        List<Source> sources = List.copyOf(selected);
        ServerProfiling.snapshot(source.getServer()).thenAccept(snapshot ->
                source.getServer().execute(() -> show(source, sources, snapshot)));
        return sources.size();
    }

    private static void show(CommandSourceStack source, List<Source> selected, ProfilerWindowSnapshot snapshot) {
        if (!ProfilerSubscriptions.mayView(source.permissions())) return;
        if (source.getEntity() instanceof ServerPlayer player && (!ProfilerSubscriptions.mayView(player.permissions())
                || source.getServer().getPlayerList().getPlayer(player.getUUID()) != player)) return;
        if (snapshot.producers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.debug.no_data"), false);
            return;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.debug.header", number(Profiler.WINDOW_TICKS), discarded(snapshot.droppedBatches())), false);
        for (ProfilerWindowSnapshot.Producer producer : snapshot.producers()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.debug.producer", producer.name(), number(producer.sampleCount()),
                    discarded(producer.discardedSpans())), false);
            for (Source selectedSource : selected) {
                ProfilerSource registered = snapshot.sources().stream().filter(value -> value.id().equals(selectedSource.id())).findFirst().orElseThrow();
                ProfilerWindowSnapshot.Statistics stats = producer.statistics(registered);
                source.sendSuccess(() -> Component.translatable("command.mxt.debug.source", selectedSource.id(),
                        millis(stats.minNanos()), millis(stats.averageNanos()), millis(stats.maxNanos()),
                        millis(stats.medianNanos()), millis(stats.p95Nanos()), millis(stats.p99Nanos()), number(stats.hits()), number(stats.samples())), false);
            }
        }
    }

    private static Component millis(double nanos) {
        return Component.literal(String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D))
                .withStyle(nanos >= 50_000_000D ? ChatFormatting.RED : nanos >= 10_000_000D ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
    }

    private static Component number(long value) {
        return Component.literal(Long.toString(value)).withStyle(ChatFormatting.AQUA);
    }

    private static Component discarded(long value) {
        return Component.literal(Long.toString(value)).withStyle(value == 0L ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
