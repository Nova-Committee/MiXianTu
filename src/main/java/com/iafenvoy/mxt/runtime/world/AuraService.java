package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.*;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.formation.builtin.AuraFormationAction;
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
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import net.minecraft.core.*;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA_QUERY)) {
            // The two nanoTime calls are only paid when the diagnostic timer is on; the query counter itself
            // is unconditional because it costs a single increment.
            boolean timing = MxtServerConfig.INSTANCE.aura.queryStats.getValue();
            long startedAt = timing ? System.nanoTime() : 0L;
            try {
                if (!(level instanceof ServerLevel server)) return resolveAura(level, pos);
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
    }

    private static AuraResult resolveAura(Level level, BlockPos pos) {
        long gameTime = level.getGameTime();
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        Resolved staticResolved = staticZone(level, pos);
        // A query never loads a chunk: outside the loaded ones there is no stock to read, so the environment is
        // the whole answer - the same trade the neighbour scan makes when it skips unloaded chunks.
        LevelChunk chunk = loadedChunk(level, pos);
        AuraChunkAttachment attachment = chunk == null ? null : ensureInitialized(chunk, pos, staticResolved);
        Resolved resolved = customZone(level, pos).orElse(staticResolved);
        // A formation's override is announced before it is applied, so a listener can refuse it; and it is only
        // announced when there is a zone holder to name, so a nameless one falls through silently.
        Optional<Resolved> formation = formationZone(level, pos);
        if (formation.isPresent() && level instanceof ServerLevel server
                && !NeoForge.EVENT_BUS.post(new AuraZoneEvent.Override(server, pos, preview(staticResolved, level, pos),
                formation.get().zone)).isCanceled()) resolved = formation.get();
        // Copied, because this map is written below and the stored pools are the stock itself.
        Map<Holder<Aura>, AuraPool> pools = attachment == null ? new LinkedHashMap<>() : AuraPool.copyAll(attachment.pools(sectionY));
        // Neither this position's own zone nor an override names one, so the chunk's stock and the nearby block
        // emitters are the whole answer: there is no environment to add on top of them.
        Holder<AuraZone> zoneHolder = resolved == null ? null : resolved.zone;
        if (pools.isEmpty()) {
            // The zone's stock is one chunk-sized amount, so a section only ever sees its own share of it.
            int sections = Math.max(1, level.getSectionsCount());
            pools(level, zoneHolder, pos, gameTime)
                    .forEach((aura, pool) -> pools.put(aura, pool.share(sections)));
        }
        // Without the centre section there is no own block part to subtract, so the neighbourhood is left out.
        if (attachment != null) applyBlockDistance(level, pools, attachment, sectionY, zoneHolder, pos, gameTime);
        if (resolved == null) return zoneless(pools);
        applyMaximumBonus(pools, resolved.maxBonus());
        return new AuraResult(pools, resolved);
    }

    // Never loads: an unloaded chunk has no readable stock, and a query must not generate terrain to find that out.
    @Nullable
    static LevelChunk loadedChunk(Level level, BlockPos pos) {
        return loadedChunk(level, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    // hasChunk tests ticket eligibility, not completion; even getChunk(FULL, false) can wait for generation.
    @Nullable
    public static LevelChunk loadedChunk(Level level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    // Seeding needs the static template and local stock, never formation overrides or neighbouring sources.
    public static void ensureInitialized(LevelChunk chunk, BlockPos pos) {
        ensureInitialized(chunk, pos, staticZone(chunk.getLevel(), pos));
    }

    private static AuraChunkAttachment ensureInitialized(LevelChunk chunk, BlockPos pos, @Nullable Resolved resolved) {
        AuraChunkAttachment attachment = chunk.getData(MxtAttachments.AURA_CHUNK);
        Holder<AuraZone> template = resolved == null ? null : resolved.zone;
        if (!attachment.ready(SectionPos.blockToSectionCoord(pos.getY())) || !Objects.equals(attachment.template(), template)) {
            initialize(chunk.getLevel(), attachment, resolved, pos);
            chunk.markUnsaved();
            if (chunk.getLevel() instanceof ServerLevel server) AuraQueryCache.invalidateResults(server);
        }
        return attachment;
    }

    // Environmental concentration only: the mutable chunk stock is deliberately excluded, so stored aura
    // cannot appear to fluctuate with the environment.
    public static AuraResult getSensedAura(Level level, BlockPos pos) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA_QUERY)) {
            AuraResult resolved = getPositionAura(level, pos);
            Holder<AuraZone> zone = resolved.source();
            if (zone == null) return resolved.copy(Map.of());
            // Copied: the zone pools are the per-tick cache, and a caller may treat what it is handed as its own.
            return resolved.copy(AuraPool.copyAll(pools(level, zone, pos, level.getGameTime())));
        }
    }

    private static AuraResult preview(@Nullable Resolved resolved, Level level, BlockPos pos) {
        return resolved == null ? zoneless(Map.of())
                : new AuraResult(AuraPool.copyAll(pools(level, resolved.zone, pos, level.getGameTime())), resolved);
    }

    // The answer for a position no zone covers: the default rules, no source holder to name, and only the pools
    // the caller passes in. EMPTY_ZONE supplies exactly those defaults, so it remains the one place they live.
    private static AuraResult zoneless(Map<Holder<Aura>, AuraPool> pools) {
        return new AuraResult(pools, EMPTY_ZONE, null, SourceKind.CHUNK);
    }

    // All-or-nothing: nothing is consumed unless every requested amount is available.
    public static boolean consume(Level level, BlockPos pos, Map<Holder<Aura>, Double> costs) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (costs.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0.0D)) return false;
            LevelChunk chunk = loadedChunk(level, pos);
            return chunk != null && chunk.getData(MxtAttachments.AURA_CHUNK).consume(SectionPos.blockToSectionCoord(pos.getY()), costs);
        }
    }

    // Missing auras are never implicitly created.
    public static void change(Level level, BlockPos pos, Map<Holder<Aura>, Double> amounts) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (amounts.values().stream().anyMatch(value -> !Double.isFinite(value))) return;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk != null)
                chunk.getData(MxtAttachments.AURA_CHUNK).change(SectionPos.blockToSectionCoord(pos.getY()), amounts);
        }
    }

    public static void initialize(Level level, AuraChunkAttachment chunk, @Nullable Resolved resolved, BlockPos pos) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            // A position no zone covers keeps no template, so its chunk holds only what its own block emitters
            // contribute; initializeAuras applies those and marks the chunk initialized either way.
            int minSectionY = level.getMinSectionY();
            int sections = level.getSectionsCount();
            if (resolved == null) {
                chunk.initializeAuras(Map.of(), minSectionY, sections);
                chunk.clearTemplate();
            } else {
                chunk.initializeAuras(computePools(resolved.zone.value(), pos, 0L), minSectionY, sections);
                chunk.setTemplate(resolved.zone);
            }
            chunk.startRegeneration(level.getGameTime());
        }
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
        Optional<Resolved> result = best(level, holder -> matchesDimension(level, holder.value(), dimension))
                .map(holder -> new Resolved(holder, SourceKind.DIMENSION, Map.of()));
        if (result.isEmpty()) {
            Holder<Biome> memoised = location == null ? null : AuraQueryCache.biome(server, location);
            if (memoised == null) {
                memoised = biome(level, pos);
                if (started != 0L) AuraQueryCache.countBiome(System.nanoTime() - started);
                if (location != null) AuraQueryCache.cacheBiome(server, location, memoised);
            }
            Holder<Biome> biome = memoised;
            result = best(level, holder -> RegistryCodecs.matches(holder.value().biomes(), biome))
                    .map(holder -> new Resolved(holder, SourceKind.BIOME, Map.of()));
        }
        if (location != null && result.isPresent())
            AuraQueryCache.cacheStaticZone(server, location, result.get());
        if (started != 0L) AuraQueryCache.countStatic(System.nanoTime() - started);
        return result.orElse(null);
    }

    // Keep vanilla's seeded biome zoom; unfinished sample chunks use the generator without loading terrain.
    static Holder<Biome> biome(Level level, BlockPos pos) {
        return level.getBiomeManager().withDifferentSource((quartX, quartY, quartZ) -> {
            LevelChunk chunk = loadedChunk(level, QuartPos.toSection(quartX), QuartPos.toSection(quartZ));
            return chunk == null ? level.getUncachedNoiseBiome(quartX, quartY, quartZ)
                    : chunk.getNoiseBiome(quartX, quartY, quartZ);
        }).getBiome(pos);
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
    // The zone names its dimensions by id, so the registry is only asked here and never while a pack loads.
    private static boolean matchesDimension(Level level, AuraZone zone, Identifier dimension) {
        if (!(level instanceof ServerLevel server))
            return RegistryCodecs.matchesKeys(zone.dimensions(), level.registryAccess().lookupOrThrow(Registries.LEVEL_STEM), dimension);
        Registry<LevelStem> registry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        if (RegistryCodecs.matchesKeys(zone.dimensions(), registry, dimension)) return true;
        return SecretRealmRegistry.aliases(dimension).anyMatch(alias -> RegistryCodecs.matchesKeys(zone.dimensions(), registry, alias));
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
        return auraModules(formation).map(AuraFormationAction::auraZone).findFirst();
    }

    private static Map<Holder<Aura>, Double> maximumBonus(Formation formation, FormulaContext context) {
        Map<Holder<Aura>, Double> values = new LinkedHashMap<>();
        auraModules(formation).forEach(aura -> aura.maxBonus().forEach((resource, provider) -> {
            double value = MiscUtil.positive(provider.evaluate(context));
            if (value > 0.0D) values.merge(resource, value, Math::max);
        }));
        return values;
    }

    private static Stream<AuraFormationAction> auraModules(Formation formation) {
        return formation.actions().stream()
                .filter(AuraFormationAction.class::isInstance)
                .map(AuraFormationAction.class::cast);
    }

    private static void applyMaximumBonus(Map<Holder<Aura>, AuraPool> pools, Map<Holder<Aura>, Double> bonuses) {
        bonuses.forEach((aura, bonus) -> {
            AuraPool pool = pools.get(aura);
            if (pool != null) pool.addMaximum(bonus);
        });
    }

    private static final int BLOCK_SEARCH_RADIUS = 3, BLOCK_EXACT_RADIUS = 1;

    // A 7x7x7 cube of sections around the query point: the inner 3x3x3 uses real source positions, the rest their
    // centre with inverse-square falloff, and a source section is only read when its chunk is already loaded.
    private static void applyBlockDistance(Level level, Map<Holder<Aura>, AuraPool> pools, AuraChunkAttachment currentChunk,
                                           int currentSectionY, Holder<AuraZone> zone, BlockPos query, long gameTime) {
        SectionPos querySection = SectionPos.of(query);
        Map<Holder<Aura>, double[]> weighted = new LinkedHashMap<>();
        int querySectionY = querySection.y();

        for (int dx = -BLOCK_SEARCH_RADIUS; dx <= BLOCK_SEARCH_RADIUS; dx++) {
            for (int dz = -BLOCK_SEARCH_RADIUS; dz <= BLOCK_SEARCH_RADIUS; dz++) {
                int sectionX = querySection.x() + dx;
                int sectionZ = querySection.z() + dz;
                LevelChunk sourceChunk = loadedChunk(level, sectionX, sectionZ);
                if (sourceChunk == null) continue;
                AuraChunkAttachment sourceAttachment = sourceChunk.getData(MxtAttachments.AURA_CHUNK);
                if (sourceAttachment.blockAuraSections().isEmpty()) continue;

                for (Entry<BlockAuraSectionCache> sectionEntry : sourceAttachment.blockAuraSections().int2ObjectEntrySet()) {
                    int sectionY = sectionEntry.getIntKey();
                    // The volume is a 7x7x7 cube of sections: a section stacked further above or below the query
                    // adds nothing at that distance and is skipped before it costs anything.
                    if (Math.abs(sectionY - querySectionY) > BLOCK_SEARCH_RADIUS) continue;
                    SectionPos section = SectionPos.of(sectionX, sectionY, sectionZ);
                    BlockPos center = sectionCenter(section);
                    // One availability per section: the stock, the visitor split and the environment do not vary
                    // inside a section, so it is resolved here instead of once per emitting block in it.
                    Map<Holder<Aura>, Double> availability = sectionAvailability(level, sourceAttachment, section, center, gameTime);
                    if (availability.isEmpty()) continue;
                    boolean exact = Math.abs(dx) <= BLOCK_EXACT_RADIUS
                            && Math.abs(sectionY - querySectionY) <= BLOCK_EXACT_RADIUS
                            && Math.abs(dz) <= BLOCK_EXACT_RADIUS;
                    if (exact) {
                        BlockAuraService.visitSources(level, sourceChunk, sectionY, (x, y, z, definitions) -> {
                            double falloff = 1.0D / Math.max(1.0D, distanceSquared(x, y, z, query));
                            for (Holder<BlockAura> definition : definitions)
                                accumulate(weighted, definition.value().aura(), availability, falloff);
                        });
                    } else {
                        double falloff = 1.0D / Math.max(1.0D, distanceSquaredToSectionCenter(section, query));
                        accumulate(weighted, sectionEntry.getValue().aura(), availability, falloff);
                    }
                }
            }
        }

        // This section's stock includes its unweighted block portion: remove that first, then add the spatially
        // weighted view of the whole neighbourhood.
        Map<Holder<Aura>, AuraValue> currentBlock = currentChunk.sectionAura(currentSectionY);
        Set<Holder<Aura>> auras = new LinkedHashSet<>(currentBlock.keySet());
        auras.addAll(weighted.keySet());
        if (auras.isEmpty()) return;
        Map<Holder<Aura>, AuraPool> environment = pools(level, zone, query, gameTime);
        for (Holder<Aura> aura : auras) {
            AuraPool shared = pools.get(aura);
            AuraValue aggregate = currentBlock.getOrDefault(aura, AuraValue.ZERO);
            double aggregateAmount = aggregate.amount();
            double aggregateMaximum = aggregate.max().resolve(aggregate.amount());
            AuraPool fallback = shared == null ? environment.get(aura) : null;
            double baseAmount = shared == null ? fallbackAmount(fallback) : Math.max(0.0D, shared.amount() - aggregateAmount);
            double baseMaximum = shared == null ? fallbackMaximum(fallback) : subtractMaximum(shared.maximum(), aggregateMaximum);
            double baseRegen = shared == null ? fallbackRegen(fallback) : Math.max(0.0D, shared.regenPerTick() - aggregate.regenPerTick());
            double[] contribution = weighted.getOrDefault(aura, new double[3]);
            double maximum = baseMaximum == Double.POSITIVE_INFINITY || Double.isInfinite(contribution[1]) ? Double.POSITIVE_INFINITY : baseMaximum + contribution[1];
            // The block part of this pool is supplied, not environment: a consumer allowed to spend the
            // ground it stands on must not also spend the field aura it is itself emitting.
            pools.computeIfAbsent(aura, ignored -> AuraPool.empty())
                    .set(baseAmount + contribution[0], maximum, baseRegen + contribution[2], contribution[0]);
        }
    }

    private static BlockPos sectionCenter(SectionPos section) {
        return new BlockPos(SectionPos.sectionToBlockCoord(section.x()) + 8,
                SectionPos.sectionToBlockCoord(section.y()) + 8, SectionPos.sectionToBlockCoord(section.z()) + 8);
    }

    private static double fallbackAmount(@Nullable AuraPool pool) {
        return pool == null ? 0.0D : pool.amount();
    }

    private static double fallbackMaximum(@Nullable AuraPool pool) {
        return pool == null ? 0.0D : pool.maximum();
    }

    private static double fallbackRegen(@Nullable AuraPool pool) {
        return pool == null ? 0.0D : pool.regenPerTick();
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

    private static double distanceSquared(int x, int y, int z, BlockPos query) {
        double dx = x - query.getX();
        double dy = y - query.getY();
        double dz = z - query.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    // Availability is already resolved for the whole section, so one contribution costs three multiplies.
    private static void accumulate(Map<Holder<Aura>, double[]> weighted, Map<Holder<Aura>, AuraValue> values,
                                   Map<Holder<Aura>, Double> availability, double falloff) {
        values.forEach((aura, value) -> {
            Double share = availability.get(aura);
            if (share == null) return;
            double weight = share * falloff;
            double[] contribution = weighted.computeIfAbsent(aura, ignored -> new double[3]);
            contribution[0] += value.amount() * weight;
            contribution[1] += value.max().resolve(value.amount()) * weight;
            contribution[2] += value.regenPerTick() * weight;
        });
    }

    // One section's availability per aura. The environment behind it is memoised per section because that is the
    // expensive half; the shared stock it is compared against stays a live read.
    private static Map<Holder<Aura>, Double> sectionAvailability(Level level, AuraChunkAttachment attachment,
                                                                 SectionPos section, BlockPos center, long gameTime) {
        Map<Holder<Aura>, AuraValue> aggregate = attachment.sectionAura(section.y());
        if (aggregate.isEmpty()) return Map.of();
        ServerLevel server = level instanceof ServerLevel value ? value : null;
        Map<Holder<Aura>, Double> result = new LinkedHashMap<>();
        List<Holder<Aura>> missing = new ArrayList<>();
        for (Holder<Aura> aura : aggregate.keySet()) {
            OptionalDouble cached = server == null ? OptionalDouble.empty()
                    : AuraQueryCache.availability(server, new AvailabilityKey(attachment, section.y(), aura));
            if (cached.isEmpty()) {
                missing.add(aura);
                continue;
            }
            double value = availability(attachment, section, aura, cached.getAsDouble());
            if (value > 0.0D) result.put(aura, value);
        }
        if (missing.isEmpty()) return result;
        long started = AuraQueryCache.timing() ? System.nanoTime() : 0L;
        try {
            Resolved zone = staticZone(level, center);
            Map<Holder<Aura>, AuraPool> environment = zone == null ? Map.of() : pools(level, zone.zone, center, gameTime);
            for (Holder<Aura> aura : missing) {
                AuraPool pool = environment.get(aura);
                double environmental = pool == null ? 0.0D : pool.amount();
                if (server != null)
                    AuraQueryCache.cacheAvailability(server, new AvailabilityKey(attachment, section.y(), aura), environmental);
                double value = availability(attachment, section, aura, environmental);
                if (value > 0.0D) result.put(aura, value);
            }
        } finally {
            if (started != 0L) AuraQueryCache.countSource(System.nanoTime() - started);
        }
        return result;
    }

    // Collapses the memoised environmental concentration and the live shared stock into an availability in
    // [0,1], split between the players who can currently see the source section.
    private static double availability(AuraChunkAttachment attachment, SectionPos section, Holder<Aura> aura, double environmental) {
        AuraValue aggregate = attachment.sectionAura(section.y()).get(aura);
        AuraPool shared = attachment.pools(section.y()).get(aura);
        if (aggregate == null || shared == null || aggregate.amount() <= 0.0D) return 0.0D;
        double available = Math.clamp((shared.amount() - environmental) / aggregate.amount(), 0.0D, 1.0D);
        return available / Math.max(1, attachment.auraVisitors(section));
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
        return Mth.lerp(v, Mth.lerp(u, a, b), Mth.lerp(u, c, d)) * noise.amplitude();
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

    public record Resolved(Holder<AuraZone> zone, SourceKind kind, Map<Holder<Aura>, Double> maxBonus) {
    }

    // The defaults a position no zone covers reports: no aura of its own, no fluctuation, no noise, the default
    // rules and no client rendering. Kept as one value so every such position answers identically.
    private static final AuraZone EMPTY_ZONE = new AuraZone(Map.of(),
            List.of(), List.of(),
            Fluctuation.NONE, Rules.DEFAULT, AlwaysCondition.INSTANCE, Distribution.EQUAL,
            0, 0, Noise.NONE, Optional.empty(), ClientRender.DEFAULT, ClientHud.NONE, Integer.MIN_VALUE);
}
