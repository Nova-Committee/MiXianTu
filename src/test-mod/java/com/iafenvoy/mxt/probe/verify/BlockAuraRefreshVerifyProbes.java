package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraChunkTicker;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraVisitorIndex;
import com.iafenvoy.mxt.util.dispatcher.TickDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.ObjIntConsumer;

public final class BlockAuraRefreshVerifyProbes {
    private BlockAuraRefreshVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/aura_refresh/timing", BlockAuraRefreshVerifyProbes::timing),
                Probe.of("verify/aura_refresh/budget", BlockAuraRefreshVerifyProbes::budget),
                Probe.of("verify/aura_refresh/lifecycle", BlockAuraRefreshVerifyProbes::lifecycle),
                Probe.of("verify/aura_refresh/clear", BlockAuraRefreshVerifyProbes::clear),
                Probe.of("verify/aura_maintenance/spread", BlockAuraRefreshVerifyProbes::spread),
                Probe.of("verify/aura_maintenance/period", BlockAuraRefreshVerifyProbes::period),
                Probe.of("verify/aura_maintenance/clock", BlockAuraRefreshVerifyProbes::clock),
                Probe.of("verify/aura_maintenance/numeric", BlockAuraRefreshVerifyProbes::numeric),
                Probe.of("verify/aura_maintenance/visitors", BlockAuraRefreshVerifyProbes::visitors),
                Probe.of("verify/aura_maintenance/visitor_model", BlockAuraRefreshVerifyProbes::visitorModel)
        );
    }

    private static void timing(ProbeContext context) {
        try (Fixture fixture = new Fixture(context)) {
            LevelChunk chunk = fixture.load(0);
            TickDispatcher<Object> dispatcher = fixture.dispatcher();
            List<Object> tasks = fixture.tasks(chunk);
            context.expect("all sections, including empty ones, have one refresh", tasks.size(), chunk.getSectionsCount());
            context.expect("initial deadlines use the inclusive random interval", tasks.stream().allMatch(task -> {
                long delay = dispatcher.nextExecution(task).orElseThrow() - 100L;
                return delay >= 600L && delay <= 1199L;
            }), true);
            Object task = tasks.getFirst();
            int sectionY = (int) read(task, "sectionY");
            fixture.mark(chunk, sectionY);
            dispatcher.reschedule(task, 1L);
            fixture.tick(100L);
            context.expect("no section rebuilds before its deadline", fixture.marked(chunk, sectionY), true);
            fixture.tick(101L);
            context.expect("refresh runs outside the configured maintenance tick", fixture.marked(chunk, sectionY), false);
            long delay = dispatcher.nextExecution(task).orElseThrow() - 101L;
            context.expect("the next random interval starts at actual execution", delay >= 600L && delay <= 1199L, true);

            fixture.mark(chunk, sectionY);
            dispatcher.reschedule(task, 1L);
            fixture.clock.setGameTime(500L);
            LevelChunk later = fixture.load(1);
            context.expect("later loads start their intervals at load time", fixture.tasks(later).stream()
                    .allMatch(key -> dispatcher.nextExecution(key).orElseThrow() >= 1100L), true);
            context.expect("loading a chunk does not dispatch another chunk's overdue work", fixture.marked(chunk, sectionY), true);
            fixture.tick(501L);
            context.expect("overdue work runs at the next post tick", fixture.marked(chunk, sectionY), false);
        }
    }

    private static void budget(ProbeContext context) {
        try (Fixture fixture = new Fixture(context)) {
            List<LevelChunk> chunks = new ArrayList<>();
            List<Object> tasks = new ArrayList<>();
            while (tasks.size() < 17) {
                LevelChunk chunk = fixture.load(chunks.size());
                chunks.add(chunk);
                tasks.addAll(fixture.tasks(chunk));
                for (int i = 0; i < chunk.getSectionsCount(); i++) fixture.mark(chunk, chunk.getMinSectionY() + i);
            }
            TickDispatcher<Object> dispatcher = fixture.dispatcher();
            tasks.forEach(task -> dispatcher.reschedule(task, 0L));
            fixture.tick(101L);
            int remaining = fixture.markedCount(chunks);
            context.expect("a concentrated deadline obeys the eight-task minimum budget", tasks.size() - remaining, 8);
            fixture.tick(101L);
            context.expect("a duplicate level post cannot drain another batch", fixture.markedCount(chunks), remaining);
            context.expect("overdue sections stay registered", dispatcher.size(), tasks.size());
            dispatcher.setLimitEnabled(false);
            fixture.tick(102L);
            context.expect("the dispatcher limit switch also applies to real section rebuilds", fixture.markedCount(chunks), 0);
            context.expect("every recurring section remains registered", dispatcher.size(), tasks.size());
        }
    }

    private static void lifecycle(ProbeContext context) {
        try (Fixture fixture = new Fixture(context)) {
            LevelChunk old = fixture.load(0);
            TickDispatcher<Object> oldDispatcher = fixture.dispatcher();
            int sectionY = old.getMinSectionY();
            fixture.mark(old, sectionY);
            AuraChunkTicker.onChunkUnload(new ChunkEvent.Unload(old));
            context.expect("unload actively cancels every section", oldDispatcher.size(), 0);
            context.expect("the last chunk releases the level queue", state("REFRESH").containsKey(fixture.level), false);

            LevelChunk replacement = fixture.load(0);
            fixture.mark(replacement, sectionY);
            TickDispatcher<Object> dispatcher = fixture.dispatcher();
            AuraChunkTicker.onChunkUnload(new ChunkEvent.Unload(old));
            context.expect("a late old unload cannot cancel the replacement", dispatcher.size(), replacement.getSectionsCount());
            oldDispatcher.tick(1000L);
            context.expect("retired tasks never touch the previous chunk", fixture.marked(old, sectionY), true);
            dispatcher.reschedule(fixture.tasks(replacement).getFirst(), 0L);
            fixture.tick(101L);
            context.expect("the same coordinates can refresh their new chunk", fixture.marked(replacement, sectionY), false);
            AuraChunkTicker.onLevelUnload(new LevelEvent.Unload(fixture.level));
            context.expect("level unload clears all callback references", dispatcher.size(), 0);
            context.expect("level unload removes the level state", state("REFRESH").containsKey(fixture.level), false);
        }
    }

    private static void clear(ProbeContext context) {
        try (Fixture fixture = new Fixture(context)) {
            LevelChunk chunk = fixture.load(0), neighbour = fixture.load(1);
            TickDispatcher<Object> dispatcher = fixture.dispatcher();
            List<Object> original = fixture.tasks(chunk);
            int sectionY = chunk.getMinSectionY();
            fixture.mark(chunk, sectionY);
            fixture.mark(neighbour, sectionY);
            original.forEach(task -> dispatcher.reschedule(task, 0L));
            Object neighbourTask = fixture.tasks(neighbour).getFirst();
            long neighbourDue = dispatcher.nextExecution(neighbourTask).orElseThrow();
            fixture.clock.setGameTime(200L);
            BlockPos center = chunk.getPos().getMiddleBlockPosition(fixture.level.getMinY());
            context.expect("radius-zero refresh touches one loaded column", AuraChunkTicker.clearBlockAuraCachesAround(fixture.level, center, 0), 1);
            context.expect("the explicit clear rebuilds synchronously", fixture.marked(chunk, sectionY), false);
            context.expect("the neighbouring column stays untouched", fixture.marked(neighbour, sectionY), true);
            context.expect("the neighbouring deadline stays untouched", dispatcher.nextExecution(neighbourTask).orElseThrow(), neighbourDue);
            List<Object> replacement = fixture.tasks(chunk);
            context.expect("explicit rebuilding never duplicates section tasks", dispatcher.size(), chunk.getSectionsCount() + neighbour.getSectionsCount());
            context.expect("explicit rebuilding restarts a full random interval", replacement.stream().allMatch(task -> {
                long delay = dispatcher.nextExecution(task).orElseThrow() - 200L;
                return delay >= 600L && delay <= 1199L;
            }), true);
            fixture.mark(chunk, sectionY);
            fixture.tick(201L);
            context.expect("old overdue deadlines cannot immediately rebuild again", fixture.marked(chunk, sectionY), true);
        }
    }

    private static Object read(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void spread(ProbeContext context) {
        try (Fixture fixture = new Fixture(context, 10)) {
            List<LevelChunk> chunks = new ArrayList<>();
            for (int i = 0; i < 30; i++) chunks.add(fixture.loadRegenerating(i));
            TickDispatcher<Object> dispatcher = fixture.maintenanceDispatcher();
            context.expect("there is exactly one regeneration task per chunk", dispatcher.size(), 30);
            for (long tick = 101L; tick <= 120L; tick++) {
                List<Double> before = chunks.stream().map(fixture::amount).toList();
                fixture.tick(tick);
                int changed = 0;
                for (int i = 0; i < chunks.size(); i++)
                    if (fixture.amount(chunks.get(i)) != before.get(i)) changed++;
                context.expect("balanced phases execute three chunks at tick " + tick, changed, 3);
            }
            double sum = chunks.stream().mapToDouble(fixture::amount).sum();
            context.expect("first partial cycles and full subsequent cycles use real elapsed ticks", sum, 465.0D);
            fixture.tick(120L);
            context.expect("repeated post does not pay twice", chunks.stream().mapToDouble(fixture::amount).sum(), sum);
            context.expect("recurring regeneration retains each chunk exactly once", dispatcher.size(), 30);
        }
    }

    private static void period(ProbeContext context) {
        try (Fixture fixture = new Fixture(context, 10)) {
            LevelChunk chunk = fixture.loadRegenerating(0);
            fixture.tick(105L);
            context.expect("a chunk waits for its phase", fixture.amount(chunk), 0.0D);
            MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.setValue(4);
            fixture.tick(106L);
            context.expect("period changes settle the previous anchor once", fixture.amount(chunk), 6.0D);
            context.expect("period changes retain one task", fixture.maintenanceDispatcher().size(), 1);
            fixture.tick(106L);
            context.expect("a repeated period-change tick does not double settle", fixture.amount(chunk), 6.0D);
            fixture.tick(108L);
            context.expect("the new phase pays only elapsed time", fixture.amount(chunk), 8.0D);
            MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.setValue(1);
            fixture.tick(109L);
            fixture.tick(110L);
            context.expect("a one-tick period advances without losing or duplicating time", fixture.amount(chunk), 10.0D);
        }
    }

    private static void clock(ProbeContext context) {
        try (Fixture fixture = new Fixture(context, 10)) {
            LevelChunk old = fixture.loadRegenerating(0);
            TickDispatcher<Object> dispatcher = fixture.maintenanceDispatcher();
            fixture.tick(110L);
            context.expect("the first full cycle pays ten", fixture.amount(old), 10.0D);
            fixture.tick(105L);
            fixture.tick(115L);
            context.expect("clock rewind resets the anchor without replaying history", fixture.amount(old), 20.0D);
            fixture.clock.setGameTime(117L);
            fixture.resetPools(old);
            fixture.tick(125L);
            context.expect("newly initialized stock does not inherit old regeneration time", fixture.amount(old), 8.0D);
            AuraChunkTicker.onChunkUnload(new ChunkEvent.Unload(old));
            context.expect("unload cancels regeneration", dispatcher.size(), 0);
            fixture.clock.setGameTime(200L);
            LevelChunk replacement = fixture.loadRegenerating(0);
            AuraChunkTicker.onChunkUnload(new ChunkEvent.Unload(old));
            context.expect("late old unload preserves the replacement", dispatcher.size(), 1);
            long due = dispatcher.nextExecution().orElseThrow();
            fixture.tick(due);
            context.expect("reload earns no offline regeneration", fixture.amount(replacement), (double) (due - 200L));
            AuraChunkTicker.onLevelUnload(new LevelEvent.Unload(fixture.level));
            context.expect("level unload clears regeneration callbacks", dispatcher.size(), 0);
            context.expect("level unload releases maintenance state", state("MAINTENANCE").containsKey(fixture.level), false);
        }
    }

    private static void numeric(ProbeContext context) {
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        AuraChunkAttachment attachment = new AuraChunkAttachment();
        attachment.initializeAuras(Map.of(aura, AuraPool.natural(0.0D, Double.POSITIVE_INFINITY, Double.MAX_VALUE)), 0, 1);
        attachment.startRegeneration(0L);
        context.expect("positive overflow changes the stock", attachment.regenerateAurasUntil(2L), true);
        context.expect("unlimited pools still retain finite amounts", attachment.pools(0).get(aura).amount(), Double.MAX_VALUE);
        context.expect("unchanged saturated pools do not report writes", attachment.regenerateAurasUntil(3L), false);
        attachment.initializeAuras(Map.of(aura, AuraPool.natural(10.0D, 10.0D, -Double.MAX_VALUE)), 0, 1);
        attachment.startRegeneration(0L);
        attachment.regenerateAurasUntil(2L);
        context.expect("negative overflow drains to zero", attachment.pools(0).get(aura).amount(), 0.0D);
        context.expect("empty negative-regeneration pools do not report writes", attachment.regenerateAurasUntil(3L), false);
        attachment.initializeAuras(Map.of(aura, AuraPool.natural(0.0D, 100.0D, 1.0D)), 0, 1);
        attachment.regenerateAurasUntil(100L);
        context.expect("an unanchored initialization receives no historical payout", attachment.pools(0).get(aura).amount(), 0.0D);
    }

    private static void visitors(ProbeContext context) {
        AuraVisitorIndex index = new AuraVisitorIndex(-4, 20);
        Map<SectionPos, Integer> counts = new HashMap<>();
        ObjIntConsumer<SectionPos> changes = (section, delta) -> {
            int count = counts.getOrDefault(section, 0) + delta;
            if (count == 0) counts.remove(section);
            else counts.put(section, count);
        };
        UUID first = new UUID(0L, 1L), second = new UUID(0L, 2L);
        context.expect("one player adds the full coverage", index.update(first, SectionPos.of(0, 5, 0), changes), 343);
        context.expect("stationary players cause no coverage writes", index.update(first, SectionPos.of(0, 5, 0), changes), 0);
        context.expect("moving one section changes only the two boundary faces", index.update(first, SectionPos.of(1, 5, 0), changes), 98);
        index.update(second, SectionPos.of(1, 5, 0), changes);
        context.expect("overlapping players share a count of two", counts.values().stream().allMatch(value -> value == 2), true);
        index.retain(Set.of(second), changes);
        context.expect("leaving removes only the departing player's share", counts.values().stream().allMatch(value -> value == 1), true);
        index.retain(Set.of(), changes);
        context.expect("no players leave no coverage entries", counts.size(), 0);
        context.expect("coverage is clipped at the bottom of the level", index.update(first, SectionPos.of(0, -4, 0), changes), 196);
        context.expect("teleporting replaces both clipped boxes", index.update(first, SectionPos.of(20, 19, 20), changes), 392);
        context.expect("height clipping retains only valid sections", counts.keySet().stream().allMatch(section -> section.y() >= -4 && section.y() < 20), true);
    }

    private static void visitorModel(ProbeContext context) {
        AuraVisitorIndex index = new AuraVisitorIndex(-4, 20);
        Map<UUID, SectionPos> players = new HashMap<>();
        Map<SectionPos, Integer> actual = new HashMap<>();
        Random random = new Random(173L);
        ObjIntConsumer<SectionPos> changes = (section, delta) -> {
            int count = actual.getOrDefault(section, 0) + delta;
            if (count == 0) actual.remove(section);
            else actual.put(section, count);
        };
        for (int step = 0; step < 300; step++) {
            UUID id = new UUID(0L, random.nextInt(6));
            if (random.nextInt(4) == 0) players.remove(id);
            else {
                SectionPos center = SectionPos.of(random.nextInt(15) - 7, random.nextInt(30) - 7, random.nextInt(15) - 7);
                players.put(id, center);
                index.update(id, center, changes);
            }
            index.retain(players.keySet(), changes);
            Map<SectionPos, Integer> expected = new HashMap<>();
            for (SectionPos center : players.values()) {
                for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
                    int y = center.y() + dy;
                    if (y >= -4 && y < 20) expected.merge(SectionPos.of(center.x() + dx, y, center.z() + dz), 1, Integer::sum);
                }
            }
            context.expect("incremental coverage matches brute force at step " + step, actual.equals(expected), true);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ServerLevel, Object> state(String name) {
        try {
            Field field = AuraChunkTicker.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Map<ServerLevel, Object>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // Detached chunks and saved level entries keep controlled ticks from changing the live arena's schedules.
    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level;
        private final ServerLevelData clock;
        private final Holder<Aura> aura;
        private final long previousTime;
        private final int previousInterval;
        private final Map<String, Object> previous = new HashMap<>();

        private Fixture(ProbeContext context) {
            this(context, 1200);
        }

        private Fixture(ProbeContext context, int interval) {
            this.level = context.level();
            this.clock = context.server().getWorldData().overworldData();
            this.aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
            this.previousTime = this.clock.getGameTime();
            this.previousInterval = MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.getValue();
            for (String name : List.of("LOADED", "PENDING", "REFRESH", "MAINTENANCE")) this.previous.put(name, state(name).remove(this.level));
            this.clock.setGameTime(100L);
            MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.setValue(interval);
        }

        private LevelChunk load(int offset) {
            LevelChunk chunk = new LevelChunk(this.level, new ChunkPos(100 + offset, 100));
            chunk.getData(MxtAttachments.AURA_CHUNK).initializeAuras(Map.of(), chunk.getMinSectionY(), chunk.getSectionsCount());
            AuraChunkTicker.onChunkLoad(new ChunkEvent.Load(chunk, false));
            return chunk;
        }

        private LevelChunk loadRegenerating(int offset) {
            LevelChunk chunk = this.load(offset);
            this.resetPools(chunk);
            return chunk;
        }

        private void resetPools(LevelChunk chunk) {
            AuraChunkAttachment aura = chunk.getData(MxtAttachments.AURA_CHUNK);
            int sections = chunk.getSectionsCount();
            aura.initializeAuras(Map.of(this.aura, AuraPool.natural(0.0D, 1000.0D * sections, sections)), chunk.getMinSectionY(), sections);
            aura.startRegeneration(this.clock.getGameTime());
        }

        private double amount(LevelChunk chunk) {
            return chunk.getData(MxtAttachments.AURA_CHUNK).pools(chunk.getMinSectionY()).get(this.aura).amount();
        }

        @SuppressWarnings("unchecked")
        private TickDispatcher<Object> maintenanceDispatcher() {
            return (TickDispatcher<Object>) read(state("MAINTENANCE").get(this.level), "dispatcher");
        }

        @SuppressWarnings("unchecked")
        private TickDispatcher<Object> dispatcher() {
            return (TickDispatcher<Object>) read(state("REFRESH").get(this.level), "dispatcher");
        }

        @SuppressWarnings("unchecked")
        private List<Object> tasks(LevelChunk chunk) {
            Map<LevelChunk, List<Object>> chunks = (Map<LevelChunk, List<Object>>) read(state("REFRESH").get(this.level), "chunks");
            return List.copyOf(chunks.get(chunk));
        }

        private void mark(LevelChunk chunk, int sectionY) {
            chunk.getData(MxtAttachments.AURA_CHUNK).setSectionAura(sectionY, Map.of(this.aura, AuraValue.ZERO));
        }

        private boolean marked(LevelChunk chunk, int sectionY) {
            return !chunk.getData(MxtAttachments.AURA_CHUNK).sectionAura(sectionY).isEmpty();
        }

        private int markedCount(List<LevelChunk> chunks) {
            return chunks.stream().mapToInt(chunk -> chunk.getData(MxtAttachments.AURA_CHUNK).blockAuraSections().size()).sum();
        }

        private void tick(long now) {
            this.clock.setGameTime(now);
            AuraChunkTicker.onLevelTick(new LevelTickEvent.Post(() -> true, this.level));
        }

        @Override
        public void close() {
            try {
                AuraChunkTicker.onLevelUnload(new LevelEvent.Unload(this.level));
                for (Map.Entry<String, Object> entry : this.previous.entrySet())
                    if (entry.getValue() != null) state(entry.getKey()).put(this.level, entry.getValue());
            } finally {
                this.clock.setGameTime(this.previousTime);
                MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.setValue(this.previousInterval);
            }
        }
    }
}
