package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.*;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.formation.builtin.BuffFormationAction;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraQueryCache.AuraLocation;
import com.iafenvoy.mxt.runtime.world.AuraQueryCache.AvailabilityKey;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Central aura resolver: the precedence is biome, dimension, custom area, then active formation.
 */
public final class AuraService {
    private AuraService() {
    }

    public static AuraResult getPositionAura(Level level, BlockPos pos) {
        // The two nanoTime calls are only paid when the diagnostic timer is on; the query counter itself
        // is unconditional because it costs a single increment.
        boolean timing = MxtServerConfig.INSTANCE.aura.queryStats.getValue();
        long startedAt = timing ? System.nanoTime() : 0L;
        try {
            if (!(level instanceof ServerLevel server)) return resolveAura(level, pos);
            // A queued block change must stay visible immediately, so the dirty flush happens before
            // the memo is consulted rather than only on a miss.
            AuraChunkTicker.flushDirty(server);
            AuraLocation location = AuraQueryCache.location(server, pos);
            Optional<AuraResult> cached = AuraQueryCache.result(server, location);
            if (cached.isPresent()) return cached.get();
            AuraResult result = resolveAura(level, pos);
            AuraQueryCache.cacheResult(server, location, result);
            return result;
        } finally {
            if (timing) AuraQueryCache.count(System.nanoTime() - startedAt);
            else AuraQueryCache.count();
        }
    }

    private static AuraResult resolveAura(Level level, BlockPos pos) {
        AuraChunkAttachment chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        Resolved staticResolved = staticZone(level, pos);
        Holder<AuraZone> template = staticResolved == null ? null : staticResolved.zone;
        if (!chunk.initialized() || !Objects.equals(chunk.template(), template))
            initialize(chunk, staticResolved, pos);
        Resolved resolved = customZone(level, pos).orElse(staticResolved);
        // A formation's override is announced before it is applied, so a listener can refuse it; and it is only
        // announced when there is a zone holder to name, so a nameless one falls through silently.
        Optional<Resolved> formation = formationZone(level, pos);
        if (formation.isPresent() && level instanceof ServerLevel server
                && !NeoForge.EVENT_BUS.post(new AuraZoneEvent.Override(server, pos, preview(staticResolved, level, pos),
                formation.get().zone)).isCanceled()) resolved = formation.get();
        Map<Holder<Aura>, AuraPool> pools = new LinkedHashMap<>(chunk.auras());
        // Neither this position's own zone nor an override names one, so the chunk's stock and the nearby block
        // emitters are the whole answer: there is no environment to add on top of them.
        Holder<AuraZone> zoneHolder = resolved == null ? null : resolved.zone;
        if (pools.isEmpty())
            pools.putAll(pools(level, zoneHolder, pos, level.getGameTime()));
        applyBlockDistance(level, pools, chunk, zoneHolder, pos, level.getGameTime());
        if (resolved == null) return zoneless(pools);
        applyMaximumBonus(pools, resolved.maxBonus());
        return new AuraResult(pools, resolved);
    }

    // Environmental concentration only: the mutable chunk stock is deliberately excluded, so stored aura
    // cannot appear to fluctuate with the environment.
    public static AuraResult getSensedAura(Level level, BlockPos pos) {
        AuraResult resolved = getPositionAura(level, pos);
        Holder<AuraZone> zone = resolved.source();
        if (zone == null) return resolved.copy(Map.of());
        return resolved.copy(pools(level, zone, pos, level.getGameTime()));
    }

    private static AuraResult preview(@Nullable Resolved resolved, Level level, BlockPos pos) {
        return resolved == null ? zoneless(Map.of()) : new AuraResult(pools(level, resolved.zone, pos, level.getGameTime()), resolved);
    }

    // The answer for a position no zone covers: the default rules, no source holder to name, and only the pools
    // the caller passes in. EMPTY_ZONE supplies exactly those defaults, so it remains the one place they live.
    private static AuraResult zoneless(Map<Holder<Aura>, AuraPool> pools) {
        return new AuraResult(pools, EMPTY_ZONE, null, SourceKind.CHUNK);
    }

    // All-or-nothing: nothing is consumed unless every requested amount is available.
    public static boolean consume(Level level, BlockPos pos, Map<Holder<Aura>, Double> costs) {
        if (costs.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0.0D)) return false;
        return level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK).consume(costs);
    }

    // Missing auras are never implicitly created.
    public static void change(Level level, BlockPos pos, Map<Holder<Aura>, Double> amounts) {
        if (amounts.values().stream().anyMatch(value -> !Double.isFinite(value))) return;
        level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK).change(amounts);
    }

    public static void initialize(AuraChunkAttachment chunk, @Nullable Resolved resolved, BlockPos pos) {
        // A position no zone covers keeps no template, so its chunk holds only what its own block emitters
        // contribute; initializeAuras applies those and marks the chunk initialized either way.
        if (resolved == null) {
            chunk.initializeAuras(Map.of());
            chunk.clearTemplate();
        }else {
            chunk.initializeAuras(computePools(resolved.zone.value(), pos, 0L));
            chunk.setTemplate(resolved.zone);
        }
        chunk.setInitialized(true);
    }

    // A dimension binding replaces a biome binding and is never outranked by a biome entry; within one
    // tier the highest priority wins. Null means no zone covers this position, which is a real state rather
    // than a failure: a pack that describes no zone for a biome leaves those chunks with only their own stock.
    @Nullable
    private static Resolved staticZone(Level level, BlockPos pos) {
        ServerLevel server = level instanceof ServerLevel value ? value : null;
        AuraLocation location = server == null ? null : AuraQueryCache.location(server, pos);
        Optional<Resolved> cached = location == null ? Optional.empty() : AuraQueryCache.staticZone(server, location);
        if (cached.isPresent()) return cached.get();
        long started = AuraQueryCache.timing() ? System.nanoTime() : 0L;
        Identifier dimension = level.dimension().identifier();
        Holder<Biome> memoised = location == null ? null : AuraQueryCache.biome(server, location);
        if (memoised == null) {
            memoised = level.getBiome(pos);
            if (started != 0L) AuraQueryCache.countBiome(System.nanoTime() - started);
            if (location != null) AuraQueryCache.cacheBiome(server, location, memoised);
        }
        Holder<Biome> biome = memoised;
        Optional<Resolved> dimensionResult = best(level, holder -> matchesDimension(level, holder.value(), dimension)).map(holder -> new Resolved(holder, SourceKind.DIMENSION, Map.of()))
                .or(() -> best(level, holder1 -> RegistryCodecs.matches(holder1.value().biomes(), biome)).map(holder1 -> new Resolved(holder1, SourceKind.BIOME, Map.of())));
        if (location != null && dimensionResult.isPresent())
            AuraQueryCache.cacheStaticZone(server, location, dimensionResult.get());
        if (started != 0L) AuraQueryCache.countStatic(System.nanoTime() - started);
        return dimensionResult.orElse(null);
    }

    private static Optional<Reference<AuraZone>> best(Level level, Predicate<Reference<AuraZone>> matches) {
        return pickHighestPriority(MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.AURA_ZONE).filter(matches));
    }

    // Highest priority wins, then registry ID ascending, so two zones cannot resolve differently
    // between reloads.
    static Optional<Reference<AuraZone>> pickHighestPriority(Stream<Reference<AuraZone>> candidates) {
        return candidates.max(Comparator.<Reference<AuraZone>>comparingInt(holder -> holder.value().priority())
                .thenComparing(holder -> holder.key().identifier(), Comparator.reverseOrder()));
    }

    // Dimension stems are a writable registry that is not synced to clients, so only the server may expand a
    // dimension tag through LEVEL_STEM; a secret realm has no level stem of its own, so it also matches aliases.
    private static boolean matchesDimension(Level level, AuraZone zone, Identifier dimension) {
        if (!(level instanceof ServerLevel server))
            return RegistryCodecs.matches(zone.dimensions(), level.registryAccess().lookupOrThrow(Registries.LEVEL_STEM), dimension);
        Registry<LevelStem> registry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        if (RegistryCodecs.matches(zone.dimensions(), registry, dimension)) return true;
        return SecretRealmRegistry.aliases(dimension).anyMatch(alias -> RegistryCodecs.matches(zone.dimensions(), registry, alias));
    }

    private static Optional<Resolved> customZone(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        return server.getData(MxtAttachments.AURA_WORLD).bestAt(pos)
                .flatMap(entry -> MxtDatapackRegistries.holder(server.registryAccess(), MxtResourceKeys.AURA_ZONE, entry.getValue().zone())
                        .map(zone -> new Resolved(zone, SourceKind.CUSTOM, Map.of())));
    }

    private static Optional<Resolved> formationZone(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        AuraLocation location = AuraQueryCache.location(server, pos);
        // The read-through lives in the cache so its two-layer result stays internal; the caller states only
        // what to compute on a miss.
        return AuraQueryCache.computeFormationZone(server, location, () -> server.getData(MxtAttachments.FORMATION_WORLD)
                .formations().entrySet().stream()
                .filter(entry -> entry.getKey().distSqr(pos) <= entry.getValue().radius() * entry.getValue().radius())
                // Nearest first, then resolved in that order until one answers: asking only the nearest array
                // would let a terrain ward inside a cultivating array hide the zone it stands in.
                .sorted(Comparator.comparingDouble(entry -> entry.getKey().distSqr(pos)))
                .map(entry -> MxtDatapackRegistries.get(server.registryAccess(), MxtResourceKeys.FORMATION, entry.getValue().formation())
                        .flatMap(formation -> auraZone(formation).map(zone -> new Resolved(zone, SourceKind.FORMATION, maximumBonus(formation, FormulaContext.of(level))))))
                .flatMap(Optional::stream)
                .findFirst());
    }

    // A formation's aura zone is a module parameter; one without such a module declares no override.
    private static Optional<Holder<AuraZone>> auraZone(Formation formation) {
        return buffModules(formation).map(BuffFormationAction::auraZone).flatMap(Optional::stream).findFirst();
    }

    private static Map<Holder<Aura>, Double> maximumBonus(Formation formation, FormulaContext context) {
        Map<Holder<Aura>, Double> values = new LinkedHashMap<>();
        buffModules(formation).forEach(buff -> buff.maxBonus().forEach((aura, provider) -> {
            double value = MiscUtil.positive(provider.evaluate(context));
            if (value > 0.0D) values.merge(aura, value, Math::max);
        }));
        return values;
    }

    private static Stream<BuffFormationAction> buffModules(Formation formation) {
        return formation.actions().stream()
                .filter(BuffFormationAction.class::isInstance)
                .map(BuffFormationAction.class::cast);
    }

    private static void applyMaximumBonus(Map<Holder<Aura>, AuraPool> pools, Map<Holder<Aura>, Double> bonuses) {
        bonuses.forEach((aura, bonus) -> pools.computeIfPresent(aura, (ignored, pool) ->
                pool.maximum() == Double.POSITIVE_INFINITY ? pool : pool.withMaximum(pool.maximum() + bonus)));
    }

    private static final int BLOCK_SEARCH_RADIUS = 3, BLOCK_EXACT_RADIUS = 1;

    // Nearby (3x3x3) sections use real source positions, the rest their centre with inverse-square falloff,
    // and a source section is only read when its chunk is already loaded.
    private static void applyBlockDistance(Level level, Map<Holder<Aura>, AuraPool> pools, AuraChunkAttachment currentChunk, Holder<AuraZone> zone, BlockPos query, long gameTime) {
        SectionPos querySection = SectionPos.of(query);
        Map<Holder<Aura>, double[]> weighted = new LinkedHashMap<>();
        int queryChunkX = querySection.x();
        int queryChunkZ = querySection.z();

        for (int dx = -BLOCK_SEARCH_RADIUS; dx <= BLOCK_SEARCH_RADIUS; dx++) {
            for (int dz = -BLOCK_SEARCH_RADIUS; dz <= BLOCK_SEARCH_RADIUS; dz++) {
                int chunkX = queryChunkX + dx;
                int chunkZ = queryChunkZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) continue;
                LevelChunk sourceChunk = level.getChunkAt(new BlockPos(chunkX << 4, query.getY(), chunkZ << 4));
                AuraChunkAttachment sourceAttachment = sourceChunk.getData(MxtAttachments.AURA_CHUNK);
                if (sourceAttachment.blockAuraSections().isEmpty()) continue;

                for (Entry<BlockAuraSectionCache> sectionEntry : sourceAttachment.blockAuraSections().int2ObjectEntrySet()) {
                    int sectionY = sectionEntry.getIntKey();
                    BlockAuraSectionCache section = sectionEntry.getValue();
                    SectionPos sectionPosition = SectionPos.of(sourceChunk.getPos().x(), sectionY, sourceChunk.getPos().z());
                    int sectionDx = sectionPosition.x() - querySection.x();
                    int sectionDy = sectionPosition.y() - querySection.y();
                    int sectionDz = sectionPosition.z() - querySection.z();
                    boolean exact = Math.abs(sectionDx) <= BLOCK_EXACT_RADIUS
                            && Math.abs(sectionDy) <= BLOCK_EXACT_RADIUS
                            && Math.abs(sectionDz) <= BLOCK_EXACT_RADIUS;
                    if (exact) {
                        for (BlockAuraContribution source : section.sources()) {
                            double falloff = 1.0D / Math.max(1.0D, distanceSquaredToBlockCenters(source.position(), query));
                            addWeighted(weighted, sourceAttachment, source.aura(), source.position(), falloff, level, gameTime);
                        }
                    } else {
                        double falloff = 1.0D / Math.max(1.0D, distanceSquaredToSectionCenter(sectionPosition, query));
                        BlockPos sectionCenter = new BlockPos(SectionPos.sectionToBlockCoord(sectionPosition.x()) + 8, SectionPos.sectionToBlockCoord(sectionPosition.y()) + 8, SectionPos.sectionToBlockCoord(sectionPosition.z()) + 8);
                        addWeighted(weighted, sourceAttachment, section.aura(), sectionCenter, falloff, level, gameTime);
                    }
                }
            }
        }

        // The current chunk's stock includes its unweighted block portion: remove that first, then add the
        // spatially weighted view.
        Map<Holder<Aura>, AuraValue> currentBlock = currentChunk.blockAura();
        Set<Holder<Aura>> auras = new LinkedHashSet<>(currentBlock.keySet());
        auras.addAll(weighted.keySet());
        if (auras.isEmpty()) return;
        Map<Holder<Aura>, AuraPool> environment = pools(level, zone, query, gameTime);
        for (Holder<Aura> aura : auras) {
            AuraPool shared = pools.get(aura);
            AuraValue aggregate = currentBlock.getOrDefault(aura, AuraValue.ZERO);
            double aggregateAmount = aggregate.amount();
            double aggregateMaximum = aggregate.max().resolve(aggregate.amount());
            double baseAmount = shared == null ? environment.getOrDefault(aura, zeroPool()).amount() : Math.max(0.0D, shared.amount() - aggregateAmount);
            double baseMaximum = shared == null ? environment.getOrDefault(aura, zeroPool()).maximum() : subtractMaximum(shared.maximum(), aggregateMaximum);
            double baseRegen = shared == null ? environment.getOrDefault(aura, zeroPool()).regenPerTick() : Math.max(0.0D, shared.regenPerTick() - aggregate.regenPerTick());
            double[] contribution = weighted.getOrDefault(aura, new double[3]);
            double maximum = baseMaximum == Double.POSITIVE_INFINITY || Double.isInfinite(contribution[1]) ? Double.POSITIVE_INFINITY : baseMaximum + contribution[1];
            // The block part of this pool is supplied, not environment: a consumer allowed to spend the
            // ground it stands on must not also spend the field aura it is itself emitting.
            pools.put(aura, new AuraPool(baseAmount + contribution[0], maximum, baseRegen + contribution[2], contribution[0]));
        }
    }

    private static AuraPool zeroPool() {
        return AuraPool.empty();
    }

    private static double subtractMaximum(double maximum, double amount) {
        return maximum == Double.POSITIVE_INFINITY ? maximum : Math.max(0.0D, maximum - amount);
    }

    private static double distanceSquaredToSectionCenter(SectionPos section, BlockPos query) {
        double centerX = SectionPos.sectionToBlockCoord(section.x()) + 8.0D;
        double centerY = SectionPos.sectionToBlockCoord(section.y()) + 8.0D;
        double centerZ = SectionPos.sectionToBlockCoord(section.z()) + 8.0D;
        double dx = query.getX() + 0.5D - centerX;
        double dy = query.getY() + 0.5D - centerY;
        double dz = query.getZ() + 0.5D - centerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSquaredToBlockCenters(BlockPos source, BlockPos query) {
        double dx = source.getX() - query.getX();
        double dy = source.getY() - query.getY();
        double dz = source.getZ() - query.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void addWeighted(Map<Holder<Aura>, double[]> weighted, AuraChunkAttachment attachment, Map<Holder<Aura>, AuraValue> values, BlockPos sourcePosition, double falloff, Level level, long gameTime) {
        values.forEach((aura, value) -> {
            double availability = sourceAvailability(level, attachment, aura, sourcePosition, gameTime);
            if (availability <= 0.0D) return;
            double[] contribution = weighted.computeIfAbsent(aura, ignored -> new double[3]);
            contribution[0] += value.amount() * availability * falloff;
            contribution[1] += value.max().resolve(value.amount()) * availability * falloff;
            contribution[2] += value.regenPerTick() * availability * falloff;
        });
    }

    private static double sourceAvailability(Level level, AuraChunkAttachment attachment, Holder<Aura> aura, BlockPos sourcePosition, long gameTime) {
        // One query asks for this once per source and per aura, so the static tier lookup and the environmental
        // pools behind it are memoised; the shared stock they are compared against stays a live read.
        long started = AuraQueryCache.timing() ? System.nanoTime() : 0L;
        try {
            ServerLevel server = level instanceof ServerLevel value ? value : null;
            AvailabilityKey key = server == null ? null
                    : new AvailabilityKey(attachment, sourcePosition.immutable(), aura);
            if (key != null) {
                OptionalDouble cached = AuraQueryCache.availability(server, key);
                if (cached.isPresent()) return availability(attachment, aura, cached.getAsDouble(), sourcePosition);
            }
            Resolved zone = staticZone(level, sourcePosition);
            double environmental = zone == null ? 0 : pools(level, zone.zone, sourcePosition, gameTime).getOrDefault(aura, zeroPool()).amount();
            if (key != null) AuraQueryCache.cacheAvailability(server, key, environmental);
            return availability(attachment, aura, environmental, sourcePosition);
        } finally {
            if (started != 0L) AuraQueryCache.countSource(System.nanoTime() - started);
        }
    }

    // Collapses the memoised environmental concentration and the live shared stock into an availability in
    // [0,1], split between the players who can currently see the source section.
    private static double availability(AuraChunkAttachment attachment, Holder<Aura> aura, double environmental, BlockPos sourcePosition) {
        AuraValue aggregate = attachment.blockAura().get(aura);
        AuraPool shared = attachment.auras().get(aura);
        if (aggregate == null || shared == null || aggregate.amount() <= 0.0D) return 0.0D;
        double available = Math.clamp((shared.amount() - environmental) / aggregate.amount(), 0.0D, 1.0D);
        int visitors = attachment.auraVisitors(SectionPos.of(sourcePosition));
        return available / Math.max(1, visitors);
    }

    private static double factor(AuraZone zone, long time) {
        if (!zone.fluctuation().enabled() || zone.fluctuation().cycleType() == CycleType.STATIC) return 1.0D;
        long cycle = zone.fluctuation().cycleType() == CycleType.DAY ? 24_000L : 192_000L;
        return Math.max(0.0D, 1.0D + zone.fluctuation().amplitude() * Math.sin(Math.PI * 2.0D * (time + zone.fluctuation().offsetTick()) / cycle));
    }

    private static double perlin(int x, int z, Noise noise) {
        if (!noise.enabled() || noise.amplitude() == 0.0D || noise.scale() <= 0.0D) return 0.0D;
        double px = x / noise.scale(), pz = z / noise.scale();
        int x0 = (int) Math.floor(px), z0 = (int) Math.floor(pz);
        double fx = px - x0, fz = pz - z0;
        double u = fade(fx), v = fade(fz);
        double a = gradient(noise.seed(), x0, z0, fx, fz), b = gradient(noise.seed(), x0 + 1, z0, fx - 1, fz);
        double c = gradient(noise.seed(), x0, z0 + 1, fx, fz - 1), d = gradient(noise.seed(), x0 + 1, z0 + 1, fx - 1, fz - 1);
        return lerp(lerp(a, b, u), lerp(c, d, u), v) * noise.amplitude();
    }

    // A pure function of the zone, the position and the tick, but one block-emitter query asks for it once per
    // contributing source, so it is memoised per tick under the zone's registry holder; the fallback is not.
    // A null holder is "no zone covers this position", which has no environmental template to read: the empty map
    // is the answer, not a lookup that would throw.
    private static Map<Holder<Aura>, AuraPool> pools(Level level, @Nullable Holder<AuraZone> holder, BlockPos pos, long gameTime) {
        if (holder == null) return Map.of();
        AuraZone zone = holder.value();
        if (!(level instanceof ServerLevel server)) return computePools(zone, pos, gameTime);
        long started = AuraQueryCache.timing() ? System.nanoTime() : 0L;
        try {
            AuraLocation location = AuraQueryCache.location(server, pos);
            Optional<Map<Holder<Aura>, AuraPool>> cached = AuraQueryCache.pools(server, location, holder);
            if (cached.isPresent()) return cached.get();
            Map<Holder<Aura>, AuraPool> pools = computePools(zone, pos, gameTime);
            AuraQueryCache.cachePools(server, location, holder, pools);
            return pools;
        } finally {
            if (started != 0L) AuraQueryCache.countPools(System.nanoTime() - started);
        }
    }

    private static Map<Holder<Aura>, AuraPool> computePools(AuraZone zone, BlockPos pos, long gameTime) {
        Map<Holder<Aura>, AuraPool> pools = new LinkedHashMap<>();
        double fluctuation = factor(zone, gameTime);
        zone.aura().forEach((aura, value) -> {
            double initial = Math.max(0.0D, (value.amount() + perlin(pos.getX(), pos.getZ(), zone.noise())) / 10.0D - 5.0D);
            pools.put(aura, AuraPool.natural(initial * fluctuation, value.max().resolve(initial), value.regenPerTick()));
        });
        return pools;
    }

    private static double gradient(long seed, int x, int z, double dx, double dz) {
        long hash = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        hash ^= hash >>> 33;
        return ((hash & 1L) == 0L ? dx : -dx) + ((hash & 2L) == 0L ? dz : -dz);
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double a, double b, double delta) {
        return a + delta * (b - a);
    }

    public record Resolved(Holder<AuraZone> zone, SourceKind kind, Map<Holder<Aura>, Double> maxBonus) {
    }

    // The defaults a position no zone covers reports: no aura of its own, no fluctuation, no noise, the default
    // rules and no client rendering. Kept as one value so every such position answers identically.
    private static final AuraZone EMPTY_ZONE = new AuraZone(Map.of(),
            List.of(), List.of(),
            Fluctuation.NONE, Rules.DEFAULT, AlwaysCondition.INSTANCE, Distribution.EQUAL,
            0, 0, Noise.NONE, Optional.empty(), ClientRender.DEFAULT, ClientHud.NONE, Integer.MIN_VALUE);
}
