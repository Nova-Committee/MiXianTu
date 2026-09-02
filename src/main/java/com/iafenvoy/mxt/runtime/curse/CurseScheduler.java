package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * Per-level due queue for curse lifecycle work. It never iterates every loaded entity.
 */
public final class CurseScheduler {
    private static final Map<ServerLevel, Queue> QUEUES = new IdentityHashMap<>();

    private CurseScheduler() {
    }

    public static void reschedule(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        Queue queue = QUEUES.computeIfAbsent(level, ignored -> new Queue());
        long due = nextDue(entity, level.getGameTime());
        if (due == Long.MAX_VALUE) {
            queue.nextDue.remove(entity.getUUID());
            return;
        }
        queue.nextDue.put(entity.getUUID(), due);
        queue.entries.add(new Entry(entity.getUUID(), due));
    }

    public static void onLevelTick(ServerLevel level) {
        Queue queue = QUEUES.get(level);
        if (queue == null) return;
        long gameTime = level.getGameTime();
        while (!queue.entries.isEmpty() && queue.entries.peek().due() <= gameTime) {
            Entry entry = queue.entries.remove();
            if (queue.nextDue.getOrDefault(entry.entity(), Long.MAX_VALUE) != entry.due()) continue;
            Entity entity = level.getEntity(entry.entity());
            if (entity == null) {
                queue.nextDue.remove(entry.entity());
                continue;
            }
            CurseService.tick(entity, gameTime, FormulaContext.of(entity));
            reschedule(entity);
        }
        if (queue.entries.isEmpty() && queue.nextDue.isEmpty()) QUEUES.remove(level);
    }

    static long nextDue(Entity entity, long gameTime) {
        CurseHolderAttachment data = entity.getData(MxtAttachments.CURSE_HOLDER);
        long result = Long.MAX_VALUE;
        for (Map.Entry<Holder<Curse>, State> entry : data.instances().entrySet()) {
            State state = entry.getValue();
            if (state.expiresAt() >= 0L) result = Math.min(result, state.expiresAt());
            double intervalValue = entry.getKey().value().tickInterval().evaluate(FormulaContext.of(entity));
            if (!Double.isFinite(intervalValue) || intervalValue <= 0.0D) continue;
            long interval = Math.max(1L, Math.round(intervalValue));
            long elapsed = Math.max(0L, gameTime - state.appliedAt());
            long next = state.appliedAt() + (Math.floorDiv(elapsed, interval) + 1L) * interval;
            result = Math.min(result, next);
        }
        return result;
    }

    private static final class Queue {
        private final PriorityQueue<Entry> entries = new PriorityQueue<>(Comparator.comparingLong(Entry::due));
        private final Map<UUID, Long> nextDue = new HashMap<>();
    }

    private record Entry(UUID entity, long due) {
    }
}
