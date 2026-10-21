package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.dispatcher.TickDispatcher;
import com.iafenvoy.mxt.util.dispatcher.TickExecutionLimit;
import com.iafenvoy.mxt.util.dispatcher.TickInterval;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

final class AuraMaintenanceState {
    private final ServerLevel level;
    private final TickDispatcher<LevelChunk> dispatcher;
    private final Map<LevelChunk, Integer> phases = new IdentityHashMap<>();
    private final AuraVisitorIndex visitors;
    private int interval;
    private int[] phaseLoads;
    private int cursor;

    AuraMaintenanceState(ServerLevel level, int interval) {
        this.level = level;
        this.visitors = new AuraVisitorIndex(level.getMinSectionY(), level.getMaxSectionY());
        this.interval = interval;
        this.phaseLoads = new int[interval];
        this.dispatcher = new TickDispatcher<>(level.getGameTime(), level.getSeed(), TickExecutionLimit.adaptive(1, interval));
    }

    void load(LevelChunk chunk, long now, int interval) {
        this.align(now, interval);
        if (this.phases.containsKey(chunk)) return;
        chunk.getData(MxtAttachments.AURA_CHUNK).startRegeneration(now);
        this.register(chunk, now);
        this.populateVisitors(chunk);
    }

    private void register(LevelChunk chunk, long now) {
        int phase = this.cursor;
        for (int i = 1; i < this.interval; i++) {
            int candidate = (this.cursor + i) % this.interval;
            if (this.phaseLoads[candidate] < this.phaseLoads[phase]) phase = candidate;
        }
        this.phaseLoads[phase]++;
        this.cursor = (phase + 1) % this.interval;
        this.phases.put(chunk, phase);
        this.dispatcher.schedule(chunk, TickInterval.fixed(this.interval), () -> this.regenerate(chunk, this.dispatcher.currentTick()));
        long delay = Math.floorMod(phase - now % this.interval, this.interval);
        this.dispatcher.reschedule(chunk, delay == 0L ? this.interval : delay);
    }

    void forget(LevelChunk chunk) {
        Integer phase = this.phases.remove(chunk);
        if (phase != null) this.phaseLoads[phase]--;
        this.dispatcher.cancel(chunk);
        chunk.getData(MxtAttachments.AURA_CHUNK).clearAuraVisitors();
    }

    void tick(long now, int interval) {
        this.align(now, interval);
        this.refreshVisitors();
        TickDispatcher.TickReport<LevelChunk> report = this.dispatcher.tick(now);
        for (TickDispatcher.TaskFailure<LevelChunk> failure : report.failures())
            MiXianTu.LOGGER.warn("Failed to regenerate aura in {} at chunk {}",
                    this.level.dimension().identifier(), failure.key().getPos(), failure.exception());
    }

    private void align(long now, int interval) {
        if (now < this.dispatcher.currentTick())
            this.phases.keySet().forEach(chunk -> chunk.getData(MxtAttachments.AURA_CHUNK).startRegeneration(now));
        this.dispatcher.advanceTo(now);
        if (interval == this.interval) return;
        List<LevelChunk> chunks = new ArrayList<>(this.phases.keySet());
        for (LevelChunk chunk : chunks) this.regenerate(chunk, now);
        this.dispatcher.clear();
        this.phases.clear();
        this.interval = interval;
        this.phaseLoads = new int[interval];
        this.cursor = 0;
        this.dispatcher.setExecutionLimit(TickExecutionLimit.adaptive(1, interval));
        for (LevelChunk chunk : chunks) this.register(chunk, now);
    }

    private void regenerate(LevelChunk chunk, long now) {
        AuraChunkAttachment aura = chunk.getData(MxtAttachments.AURA_CHUNK);
        if (!aura.seeded()) {
            aura.startRegeneration(now);
            return;
        }
        if (aura.regenerateAurasUntil(now)) {
            chunk.markUnsaved();
            AuraQueryCache.invalidateResults(this.level);
        }
    }

    private void refreshVisitors() {
        Set<UUID> present = new HashSet<>();
        int changed = 0;
        for (ServerPlayer player : this.level.players()) {
            UUID id = player.getUUID();
            present.add(id);
            changed += this.visitors.update(id, SectionPos.of(player.blockPosition()), this::changeVisitors);
        }
        changed += this.visitors.retain(present, this::changeVisitors);
        if (changed > 0) AuraQueryCache.invalidateResults(this.level);
    }

    private void changeVisitors(SectionPos section, int delta) {
        LevelChunk chunk = this.level.getChunkSource().getChunkNow(section.x(), section.z());
        if (chunk != null && this.phases.containsKey(chunk))
            chunk.getData(MxtAttachments.AURA_CHUNK).changeAuraVisitors(section, delta);
    }

    private void populateVisitors(LevelChunk chunk) {
        AuraChunkAttachment aura = chunk.getData(MxtAttachments.AURA_CHUNK);
        aura.clearAuraVisitors();
        this.visitors.forEachCenter(center -> {
            if (Math.abs(chunk.getPos().x() - center.x()) > 3 || Math.abs(chunk.getPos().z() - center.z()) > 3) return;
            int minY = Math.max(chunk.getMinSectionY(), center.y() - 3);
            int maxY = Math.min(chunk.getMaxSectionY() - 1, center.y() + 3);
            for (int y = minY; y <= maxY; y++)
                aura.addAuraVisitor(SectionPos.of(chunk.getPos().x(), y, chunk.getPos().z()));
        });
        AuraQueryCache.invalidateResults(this.level);
    }

    void close() {
        this.dispatcher.clear();
        this.phases.keySet().forEach(chunk -> chunk.getData(MxtAttachments.AURA_CHUNK).clearAuraVisitors());
        this.phases.clear();
        this.visitors.clear();
        AuraQueryCache.invalidateResults(this.level);
    }
}
