package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.runtime.world.AuraService.Resolved;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Per-tick memo for the aura resolution pipeline. Every entry is valid for at most one {@link ServerLevel}
 * tick, because the inputs are the chunk's mutable aura stock and the game time.
 */
public final class AuraQueryCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Hard ceiling per level and tick; dropping a table only costs a recomputation.
    private static final int MAX_ENTRIES = 16_384;
    // Session-wide kill switch, flipped only by the audit benchmark.
    private static volatile boolean enabled = true;
    // Mirrors the config switch; the audit turns it on without touching the config file.
    private static volatile boolean timing = false;

    public static boolean timing() {
        return timing;
    }

    public static void setTiming(boolean value) {
        timing = value;
    }

    private static final Map<ServerLevel, Long> EPOCH = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<AuraLocation, Resolved>> STATIC = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<AuraLocation, Optional<Resolved>>> FORMATION = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<LevelPosition, AuraResult>> RESULT = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<AuraLocation, Map<Holder<AuraZone>, Map<Holder<Aura>, AuraPool>>>> POOLS = new IdentityHashMap<>();
    // One query asks every source in a 7x7 chunk neighbourhood for every aura, so the same answer is
    // requested hundreds of times.
    private static final Map<ServerLevel, Map<AvailabilityKey, Double>> AVAILABILITY = new IdentityHashMap<>();
    // Each biome read is far more expensive than a map lookup, and one query asks for every nearby source.
    private static final Map<ServerLevel, Map<LevelPosition, Identifier>> BIOME = new IdentityHashMap<>();
    // Locating a holder needs a registry scan, so without this it would run once per contributing source.
    private static final Map<ServerLevel, Map<AuraZone, Optional<Holder<AuraZone>>>> HOLDER = new IdentityHashMap<>();

    // Unconditional: one increment is far cheaper than the two System.nanoTime() calls the timer needs.
    private static final AtomicLong QUERIES = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();

    // Per-layer hit and miss counters, so a live server can say which layer is still doing real work.
    private static final AtomicLong STATIC_HITS = new AtomicLong();
    private static final AtomicLong STATIC_MISSES = new AtomicLong();
    private static final AtomicLong POOLS_HITS = new AtomicLong();
    private static final AtomicLong POOLS_MISSES = new AtomicLong();
    private static final AtomicLong RESULT_HITS = new AtomicLong();
    private static final AtomicLong FORMATION_HITS = new AtomicLong();
    private static final AtomicLong FORMATION_MISSES = new AtomicLong();
    private static final AtomicLong HOLDER_HITS = new AtomicLong();
    private static final AtomicLong HOLDER_MISSES = new AtomicLong();
    private static final AtomicLong AVAILABILITY_HITS = new AtomicLong();
    private static final AtomicLong AVAILABILITY_MISSES = new AtomicLong();
    private static final AtomicLong BIOME_HITS = new AtomicLong();
    private static final AtomicLong BIOME_MISSES = new AtomicLong();
    private static final AtomicLong TICKS = new AtomicLong();

    private AuraQueryCache() {
    }

    // Hit ratios are the point: a layer at ninety percent misses needs a cheaper computation, not more caching.
    public static void reportDiagnostics() {
        long ticks = TICKS.getAndSet(0L);
        if (ticks == 0L) return;
        LOGGER.info("[mxt] aura resolver over {} level ticks: {} queries ({} us each); "
                        + "biome {} hit / {} miss, static {} hit / {} miss, pools {} hit / {} miss, result {} hit, "
                        + "formation {} hit / {} miss, holder {} hit / {} miss, availability {} hit / {} miss",
                ticks, QUERIES.get(), usPerQuery(), BIOME_HITS.get(), BIOME_MISSES.get(),
                STATIC_HITS.get(), STATIC_MISSES.get(),
                POOLS_HITS.get(), POOLS_MISSES.get(), RESULT_HITS.get(), FORMATION_HITS.get(),
                FORMATION_MISSES.get(), HOLDER_HITS.get(), HOLDER_MISSES.get(),
                AVAILABILITY_HITS.get(), AVAILABILITY_MISSES.get());
    }

    // Returns -1 when the diagnostic timer never ran.
    public static double usPerQuery() {
        long queries = QUERIES.get();
        if (queries == 0L) return -1.0D;
        return NANOS.get() / 1000.0D / queries;
    }

    public record Stats(long availabilityHits, long availabilityMisses, long staticHits, long staticMisses,
                        long poolsHits, long poolsMisses, long resultHits, long formationHits, long formationMisses,
                        long holderHits, long holderMisses) {
    }

    public static Stats stats() {
        return new Stats(AVAILABILITY_HITS.get(), AVAILABILITY_MISSES.get(), STATIC_HITS.get(), STATIC_MISSES.get(),
                POOLS_HITS.get(), POOLS_MISSES.get(), RESULT_HITS.get(), FORMATION_HITS.get(), FORMATION_MISSES.get(),
                HOLDER_HITS.get(), HOLDER_MISSES.get());
    }

    private static final AtomicLong BIOME_NANOS = new AtomicLong();
    private static final AtomicLong BIOME_CALLS = new AtomicLong();
    private static final AtomicLong STATIC_NANOS = new AtomicLong();
    private static final AtomicLong POOLS_NANOS = new AtomicLong();
    private static final AtomicLong SOURCE_NANOS = new AtomicLong();

    public static void countBiome(long nanos) {
        BIOME_CALLS.incrementAndGet();
        BIOME_NANOS.addAndGet(nanos);
    }

    public static void countStatic(long nanos) {
        STATIC_NANOS.addAndGet(nanos);
    }

    public static void countPools(long nanos) {
        POOLS_NANOS.addAndGet(nanos);
    }

    public static void countSource(long nanos) {
        SOURCE_NANOS.addAndGet(nanos);
    }

    public static void reportStageCosts() {
        long queries = Math.max(1L, QUERIES.get());
        long biomeCalls = BIOME_CALLS.getAndSet(0L);
        long biomeNanos = BIOME_NANOS.getAndSet(0L);
        long staticNanos = STATIC_NANOS.getAndSet(0L);
        long poolsNanos = POOLS_NANOS.getAndSet(0L);
        long sourceNanos = SOURCE_NANOS.getAndSet(0L);
        LOGGER.info("[mxt] aura stage cost per query: biome {} calls / {} us, static {} us, pools {} us, sources {} us",
                biomeCalls / queries, biomeNanos / 1000L / queries, staticNanos / 1000L / queries,
                poolsNanos / 1000L / queries, sourceNanos / 1000L / queries);
    }

    // Opens the next tick window and drops everything computed under the previous one; called once per
    // level tick, at the very end.
    public static void advance(ServerLevel level, long gameTime) {
        EPOCH.put(level, gameTime);
        STATIC.remove(level);
        FORMATION.remove(level);
        RESULT.remove(level);
        POOLS.remove(level);
        AVAILABILITY.remove(level);
        BIOME.remove(level);
        TICKS.incrementAndGet();
    }

    // An empty optional is a real answer, so only a missing map entry misses; a null scan result is stored
    // as empty so a caller's .orElse(null) sees a miss rather than an NPE.
    static Optional<Holder<AuraZone>> holder(ServerLevel level, AuraZone zone) {
        if (!enabled) return Optional.empty();
        Map<AuraZone, Optional<Holder<AuraZone>>> cache = HOLDER.get(level);
        if (cache != null) {
            Optional<Holder<AuraZone>> cached = cache.get(zone);
            if (cached != null) {
                HOLDER_HITS.incrementAndGet();
                return cached;
            }
        }
        HOLDER_MISSES.incrementAndGet();
        Optional<Holder<AuraZone>> found = AuraService.findHolder(zone);
        if (cache == null || cache.size() >= MAX_ENTRIES) {
            cache = new IdentityHashMap<>();
            HOLDER.put(level, cache);
        }
        cache.put(zone, found);
        return found;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) clear();
    }

    static void clear() {
        EPOCH.clear();
        STATIC.clear();
        FORMATION.clear();
        RESULT.clear();
        POOLS.clear();
        AVAILABILITY.clear();
        BIOME.clear();
        HOLDER.clear();
        LAST_QUERY.clear();
    }

    private static final Map<ServerLevel, Map<UUID, AuraLocation>> LAST_QUERY = new IdentityHashMap<>();

    public static boolean needsQuery(ServerLevel level, UUID entity, AuraLocation position, int refreshInterval) {
        Map<UUID, AuraLocation> tracked = LAST_QUERY.get(level);
        AuraLocation previous = tracked == null ? null : tracked.get(entity);
        if (previous == null) return true;
        if (!previous.dimension().equals(position.dimension())) return true;
        if (!previous.pos().equals(position.pos())) return true;
        long age = position.gameTime() - previous.gameTime();
        // A zero or negative age means this tick was already resolved, or the level clock did not move.
        if (age <= 0L) {
            SKIPPED.incrementAndGet();
            return false;
        }
        if (age >= refreshInterval) return true;
        SKIPPED.incrementAndGet();
        return false;
    }

    private static final AtomicLong SKIPPED = new AtomicLong();

    public static long skipped() {
        return SKIPPED.get();
    }

    // Only called when a resolution actually ran, so a skipped tick never extends the staleness window.
    public static void recordQuery(ServerLevel level, UUID entity, AuraLocation position) {
        LAST_QUERY.computeIfAbsent(level, ignored -> new HashMap<>()).put(entity, position);
    }

    public static void forget(ServerLevel level, UUID entity) {
        Map<UUID, AuraLocation> tracked = LAST_QUERY.get(level);
        if (tracked != null) tracked.remove(entity);
    }

    // Drops the counters but keeps the memo, so a benchmark can measure the two halves of one run separately.
    public static void resetStats() {
        QUERIES.set(0L);
        NANOS.set(0L);
    }

    public static long queries() {
        return QUERIES.get();
    }

    public static long nanos() {
        return NANOS.get();
    }

    static void count(long elapsedNanos) {
        QUERIES.incrementAndGet();
        NANOS.addAndGet(elapsedNanos);
    }

    // Untimed variant, used when the diagnostic timer is off.
    static void count() {
        QUERIES.incrementAndGet();
    }

    // Built without a registry lookup, so keying the memo never costs more than the memo saves.
    static AuraLocation location(ServerLevel level, BlockPos pos) {
        return new AuraLocation(level.dimension().identifier(), pos.immutable(), level.getGameTime());
    }

    // Returns null on a miss so the caller can do the expensive read itself.
    static Identifier biome(ServerLevel level, AuraLocation location) {
        if (!enabled || !current(level, location)) return null;
        Map<LevelPosition, Identifier> cache = BIOME.get(level);
        Identifier cached = cache == null ? null : cache.get(new LevelPosition(location.pos(), location.gameTime()));
        if (cached == null) {
            BIOME_MISSES.incrementAndGet();
            return null;
        }
        BIOME_HITS.incrementAndGet();
        return cached;
    }

    static void cacheBiome(ServerLevel level, AuraLocation location, Identifier biome) {
        if (!enabled || !current(level, location)) return;
        Map<LevelPosition, Identifier> cache = BIOME.get(level);
        if (cache == null || cache.size() >= MAX_ENTRIES) {
            cache = new HashMap<>();
            BIOME.put(level, cache);
        }
        cache.put(new LevelPosition(location.pos(), location.gameTime()), biome);
    }

    static Optional<AuraResult> result(ServerLevel level, AuraLocation location) {
        if (!enabled || !current(level, location)) return Optional.empty();
        Map<LevelPosition, AuraResult> cache = RESULT.get(level);
        AuraResult cached = cache == null ? null : cache.get(new LevelPosition(location.pos(), location.gameTime()));
        if (cached == null) return Optional.empty();
        RESULT_HITS.incrementAndGet();
        return Optional.of(cached);
    }

    static void cacheResult(ServerLevel level, AuraLocation location, AuraResult result) {
        if (!enabled || !current(level, location)) return;
        Map<LevelPosition, AuraResult> cache = RESULT.get(level);
        if (cache == null || cache.size() >= MAX_ENTRIES) {
            cache = new HashMap<>();
            RESULT.put(level, cache);
        }
        cache.put(new LevelPosition(location.pos(), location.gameTime()), result);
    }

    static Optional<Resolved> staticZone(ServerLevel level, AuraLocation location) {
        if (!enabled || !current(level, location)) return Optional.empty();
        Map<AuraLocation, Resolved> cache = STATIC.get(level);
        Resolved cached = cache == null ? null : cache.get(location);
        if (cached == null) {
            STATIC_MISSES.incrementAndGet();
            return Optional.empty();
        }
        STATIC_HITS.incrementAndGet();
        return Optional.of(cached);
    }

    static void cacheStaticZone(ServerLevel level, AuraLocation location, Resolved value) {
        if (!enabled || !current(level, location)) return;
        Map<AuraLocation, Resolved> cache = STATIC.get(level);
        if (cache == null || cache.size() >= MAX_ENTRIES) {
            cache = new HashMap<>();
            STATIC.put(level, cache);
        }
        cache.put(location, value);
    }

    // The outer layer answers "was this position memoised?", the inner optional is the answer itself - often "no
    // array covers here" - and only optionals are stored, so a null lookup means the key is absent.
    @SuppressWarnings("OptionalAssignedToNull")
    static Optional<Resolved> computeFormationZone(ServerLevel level, AuraLocation location, Supplier<Optional<Resolved>> compute) {
        if (!enabled || !current(level, location)) return compute.get();
        Map<AuraLocation, Optional<Resolved>> cache = FORMATION.get(level);
        Optional<Resolved> cached = cache == null ? null : cache.get(location);
        if (cached != null) {
            FORMATION_HITS.incrementAndGet();
            return cached;
        }
        FORMATION_MISSES.incrementAndGet();
        Optional<Resolved> resolved = compute.get();
        if (cache == null || cache.size() >= MAX_ENTRIES) {
            cache = new HashMap<>();
            FORMATION.put(level, cache);
        }
        cache.put(location, resolved);
        return resolved;
    }

    static Optional<Map<Holder<Aura>, AuraPool>> pools(ServerLevel level, AuraLocation location, Holder<AuraZone> zone) {
        if (!enabled || !current(level, location)) return Optional.empty();
        Map<AuraLocation, Map<Holder<AuraZone>, Map<Holder<Aura>, AuraPool>>> zones = POOLS.get(level);
        Map<Holder<AuraZone>, Map<Holder<Aura>, AuraPool>> byZone = zones == null ? null : zones.get(location);
        Map<Holder<Aura>, AuraPool> cached = byZone == null ? null : byZone.get(zone);
        if (cached == null) {
            POOLS_MISSES.incrementAndGet();
            return Optional.empty();
        }
        POOLS_HITS.incrementAndGet();
        return Optional.of(cached);
    }

    static void cachePools(ServerLevel level, AuraLocation location, Holder<AuraZone> zone,
                           Map<Holder<Aura>, AuraPool> pools) {
        if (!enabled || !current(level, location)) return;
        Map<AuraLocation, Map<Holder<AuraZone>, Map<Holder<Aura>, AuraPool>>> zones = POOLS.get(level);
        if (zones == null || zones.size() >= MAX_ENTRIES) {
            zones = new HashMap<>();
            POOLS.put(level, zones);
        }
        zones.computeIfAbsent(location, ignored -> new IdentityHashMap<>()).put(zone, pools);
    }

    // True when the key still belongs to the level's current tick window.
    private static boolean current(ServerLevel level, AuraLocation location) {
        Long epoch = EPOCH.get(level);
        return epoch != null && epoch == location.gameTime();
    }

    static Optional<Double> availability(ServerLevel level, AvailabilityKey key) {
        if (!enabled) return Optional.empty();
        Map<AvailabilityKey, Double> cache = AVAILABILITY.get(level);
        Double cached = cache == null ? null : cache.get(key);
        if (cached == null) {
            AVAILABILITY_MISSES.incrementAndGet();
            return Optional.empty();
        }
        AVAILABILITY_HITS.incrementAndGet();
        return Optional.of(cached);
    }

    static void cacheAvailability(ServerLevel level, AvailabilityKey key, double value) {
        if (!enabled) return;
        Map<AvailabilityKey, Double> cache = AVAILABILITY.get(level);
        if (cache == null || cache.size() >= MAX_ENTRIES) {
            cache = new HashMap<>();
            AVAILABILITY.put(level, cache);
        }
        cache.put(key, value);
    }

    // The chunk attachment is part of the key, so two chunks that happen to describe the same source
    // position can never share an entry.
    record AvailabilityKey(Object attachment, BlockPos source, Holder<Aura> aura) {
    }

    // The dimension travels with the position so one level's snapshot can never be compared against
    // another's; the game time makes the epoch check cheap.
    public record AuraLocation(Identifier dimension, BlockPos pos, long gameTime) {
    }

    // The block-emitter falloff is position sensitive, so a full result is keyed by the exact queried block.
    private record LevelPosition(BlockPos pos, long gameTime) {
    }
}
