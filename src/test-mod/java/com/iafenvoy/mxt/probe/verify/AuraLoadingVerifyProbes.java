package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.AuraDistributionService;
import com.iafenvoy.mxt.runtime.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class AuraLoadingVerifyProbes {
    private AuraLoadingVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/aura_loading/ready_only", AuraLoadingVerifyProbes::readyOnly),
                Probe.of("verify/aura_loading/neighbours", AuraLoadingVerifyProbes::neighbours),
                Probe.of("verify/aura_loading/biome", AuraLoadingVerifyProbes::biome),
                Probe.of("verify/aura_loading/seeding", AuraLoadingVerifyProbes::seeding),
                Probe.of("verify/aura_loading/distribution", AuraLoadingVerifyProbes::distribution)
        );
    }

    private static void readyOnly(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.player().blockPosition();
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        LevelChunk ready = level.getChunkAt(pos);
        context.check("a completed chunk remains directly readable", AuraService.loadedChunk(level, ready.getPos().x(), ready.getPos().z()) == ready);
        try (SuspendedChunk suspended = new SuspendedChunk(level, ready.getPos())) {
            context.check("ticket eligibility still reports the unfinished chunk", level.getChunkSource().hasChunk(ready.getPos().x(), ready.getPos().z()));
            context.check("unfinished FULL is skipped", AuraService.loadedChunk(level, ready.getPos().x(), ready.getPos().z()) == null);
            WorldAuraAccess access = new WorldAuraAccess(level, pos);
            context.check("unfinished stock has no readable capacity", access.getCapacity(null, context.registries()).isEmpty());
            context.expect("unfinished stock refuses extraction", access.extract(null, context.registries(), aura, 1.0D, true, false), 1.0D);
            context.check("consume refuses unfinished terrain", !AuraService.consume(level, pos, Map.of(aura, 1.0D)));
            AuraService.change(level, pos, Map.of(aura, 1.0D));
            AuraService.getPositionAura(level, pos);
            context.check("query and transfer add no ticket", suspended.ticketsUnchanged());
        }
        context.check("completion becomes visible again", AuraService.loadedChunk(level, ready.getPos().x(), ready.getPos().z()) == ready);
    }

    private static void neighbours(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.player().blockPosition();
        ChunkPos center = context.player().chunkPosition();
        ChunkPos neighbour = new ChunkPos(center.x() + 1, center.z());
        try (SuspendedChunk suspended = new SuspendedChunk(level, neighbour)) {
            AuraResult result = AuraService.getPositionAura(level, pos);
            context.check("a query next to unfinished terrain still resolves its own region", result.source() != null);
            context.check("the neighbour remains unfinished after the complete query", AuraService.loadedChunk(level, neighbour.x(), neighbour.z()) == null);
            context.check("neighbour scans never add a loading ticket", suspended.ticketsUnchanged());
        }
    }

    private static void biome(ProbeContext context) {
        ServerLevel level = context.level();
        ChunkPos target = context.player().chunkPosition();
        for (int x : new int[]{0, 1, 8, 14, 15}) {
            BlockPos pos = new BlockPos(target.getMinBlockX() + x, context.player().blockPosition().getY(), target.getMinBlockZ() + 8);
            context.expect("ready biome preserves vanilla zoom at local x=" + x, biome(level, pos), level.getBiome(pos));
        }
        BlockPos center = target.getMiddleBlockPosition(context.player().blockPosition().getY());
        Holder<Biome> generated = level.getBiomeManager().withDifferentSource(level::getUncachedNoiseBiome).getBiome(center);
        try (SuspendedChunk suspended = new SuspendedChunk(level, target)) {
            context.expect("an unfinished biome sample uses the generator with vanilla zoom", biome(level, center), generated);
            context.check("biome fallback leaves tickets unchanged", suspended.ticketsUnchanged());
        }
    }

    private static Holder<Biome> biome(ServerLevel level, BlockPos pos) {
        @SuppressWarnings("unchecked") Holder<Biome> biome = (Holder<Biome>) invoke(AuraService.class, "biome",
                new Class<?>[]{Level.class, BlockPos.class}, level, pos);
        return biome;
    }

    private static void seeding(ProbeContext context) {
        ServerLevel level = context.level();
        try (TickerState _ = new TickerState(level)) {
            LevelChunk chunk = new LevelChunk(level, new ChunkPos(100_000, 100_000));
            AuraChunkTicker.onChunkLoad(new ChunkEvent.Load(chunk, true));
            chunk.tryMarkSaved();
            long queries = AuraQueryCache.queries();
            AuraChunkTicker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
            AuraChunkAttachment attachment = chunk.getData(MxtAttachments.AURA_CHUNK);
            context.check("the pending chunk itself is initialized", attachment.seeded());
            context.expect("seeding never invokes a complete aura query", AuraQueryCache.queries(), queries);
            context.expect("every section gets its own initialized map", attachment.auras().size(), chunk.getSectionsCount());
            context.check("initialization marks the owning chunk for saving", chunk.isUnsaved());
            Map<Holder<Aura>, AuraPool> original = attachment.pools(chunk.getMinSectionY());
            chunk.tryMarkSaved();
            AuraService.ensureInitialized(chunk, chunk.getPos().getMiddleBlockPosition(level.getMinY()));
            context.check("repeated initialization preserves existing stock", attachment.pools(chunk.getMinSectionY()) == original);
            context.check("a ready chunk is not marked dirty again", !chunk.isUnsaved());
            context.check("seeding does not load the detached chunk's terrain", AuraService.loadedChunk(level, 100_000, 100_000) == null);
        }
    }

    private static void distribution(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos controller = context.player().blockPosition();
        ChunkPos center = context.player().chunkPosition();
        ChunkPos neighbour = new ChunkPos(center.x() + 1, center.z());
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        try (SuspendedChunk suspended = new SuspendedChunk(level, neighbour)) {
            invoke(AuraDistributionService.class, "ground", new Class<?>[]{ServerLevel.class, BlockPos.class, double.class}, level, controller, 32.0D);
            Class<?> slotType = Arrays.stream(AuraDistributionService.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("Slot")).findFirst().orElseThrow();
            Object slot;
            try {
                var constructor = slotType.getDeclaredConstructor(long.class, int.class);
                constructor.setAccessible(true);
                slot = constructor.newInstance(neighbour.pack(), SectionPos.blockToSectionCoord(controller.getY()));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
            invoke(AuraDistributionService.class, "reserve", new Class<?>[]{ServerLevel.class, long.class, long.class, slotType, List.class},
                    level, level.getGameTime(), 0L, slot, List.of());
            Map<Holder<Aura>, Double> taken = new LinkedHashMap<>();
            invoke(AuraDistributionService.class, "withdraw", new Class<?>[]{ServerLevel.class, slotType, Map.class, Map.class},
                    level, slot, Map.of(aura, 1.0D), taken);
            context.check("unfinished formation sources pay nothing", taken.isEmpty());
            context.check("formation scan, reservation and withdrawal add no ticket", suspended.ticketsUnchanged());
        }
    }

    private static Object read(Class<?> owner, Object instance, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException failure) throw failure;
            throw new IllegalStateException(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // An eligible real holder with unfinished futures reproduces the hasChunk/getChunk distinction without waiting.
    private static final class SuspendedChunk implements AutoCloseable {
        private final ServerChunkCache source;
        private final ChunkPos pos;
        private final AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures;
        private final Map<Integer, CompletableFuture<ChunkResult<ChunkAccess>>> previous = new HashMap<>();
        private final TicketStorage tickets;
        private final List<Ticket> previousTickets;
        private final boolean memoEnabled;

        @SuppressWarnings("unchecked")
        private SuspendedChunk(ServerLevel level, ChunkPos pos) {
            this.source = level.getChunkSource();
            this.pos = pos;
            level.getChunk(pos.x(), pos.z());
            ChunkHolder holder = this.source.chunkMap.getVisibleChunkIfPresent(pos.pack());
            if (holder == null || holder.currentlyLoading != null) throw new IllegalStateException("Probe needs a completed chunk holder");
            this.futures = (AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>>) read(GenerationChunkHolder.class, holder, "futures");
            this.tickets = (TicketStorage) read(ServerChunkCache.class, this.source, "ticketStorage");
            this.previousTickets = List.copyOf(this.tickets.getTickets(pos.pack()));
            this.memoEnabled = (boolean) read(AuraQueryCache.class, null, "enabled");
            for (ChunkStatus status : List.of(ChunkStatus.BIOMES, ChunkStatus.FULL)) {
                int index = status.getIndex();
                this.previous.put(index, this.futures.get(index));
                this.futures.set(index, new CompletableFuture<>() {
                    @Override
                    public boolean isDone() {
                        throw new IllegalStateException("Aura entered the blocking chunk scheduling path");
                    }
                });
            }
            this.clearChunkCache();
            AuraQueryCache.setEnabled(false);
        }

        private boolean ticketsUnchanged() {
            return this.previousTickets.equals(this.tickets.getTickets(this.pos.pack()));
        }

        private void clearChunkCache() {
            try {
                Method method = ServerChunkCache.class.getDeclaredMethod("clearCache");
                method.setAccessible(true);
                method.invoke(this.source);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void close() {
            this.previous.forEach(this.futures::set);
            this.clearChunkCache();
            for (Ticket ticket : List.copyOf(this.tickets.getTickets(this.pos.pack())))
                if (!this.previousTickets.contains(ticket)) this.tickets.removeTicket(ticket, this.pos);
            AuraQueryCache.setEnabled(this.memoEnabled);
        }
    }

    private static final class TickerState implements AutoCloseable {
        private final ServerLevel level;
        private final Map<Map<ServerLevel, Object>, Object> previous = new IdentityHashMap<>();

        @SuppressWarnings("unchecked")
        private TickerState(ServerLevel level) {
            this.level = level;
            for (String name : List.of("LOADED", "PENDING", "REFRESH", "MAINTENANCE")) {
                Map<ServerLevel, Object> state = (Map<ServerLevel, Object>) read(AuraChunkTicker.class, null, name);
                this.previous.put(state, state.remove(level));
            }
        }

        @Override
        public void close() {
            AuraChunkTicker.onLevelUnload(new LevelEvent.Unload(this.level));
            this.previous.forEach((state, value) -> {
                if (value != null) state.put(this.level, value);
            });
        }
    }
}
