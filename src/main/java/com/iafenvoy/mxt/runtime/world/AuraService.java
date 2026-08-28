package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientHud;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientRender;
import com.iafenvoy.mxt.data.aura.AuraZone.CycleType;
import com.iafenvoy.mxt.data.aura.AuraZone.Fluctuation;
import com.iafenvoy.mxt.data.aura.AuraZone.Noise;
import com.iafenvoy.mxt.data.aura.AuraZone.Rules;
import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * Central aura resolver. Its precedence is biome, dimension, custom area, then active formation.
 */
public final class AuraService {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");

    private AuraService() {
    }

    public static AuraResult getPositionAura(Level level, BlockPos pos) {
        AuraChunkAttachment chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        Resolved staticResolved = staticZone(level, pos);
        if (!chunk.initialized() || !chunk.template().equals(staticResolved.holder()))
            initialize(chunk, staticResolved, pos);
        Resolved resolved = staticResolved;
        Resolved lower = resolved;
        resolved = customZone(level, pos).orElse(resolved);
        Resolved formation = formationZone(level, pos).orElse(null);
        if (formation != null && level instanceof ServerLevel server
                && !NeoForge.EVENT_BUS.post(new AuraZoneEvent.Override(server, pos, preview(lower, level, pos), formation.id())).isCanceled()) {
            resolved = formation;
        }
        Map<Holder<Resource>, AuraPool> pools = new LinkedHashMap<>(chunk.auras());
        if (pools.isEmpty()) pools = pools(resolved.definition(), pos, level.getGameTime());
        applyMaximumBonus(pools, resolved.maxBonus());
        return new AuraResult(pools, resolved.definition().auraKinds(),
                resolved.definition().rules(), resolved.definition().elementFitBonus(), resolved.definition().elementConflictPenalty(),
                resolved.definition().cultivateCondition(), resolved.definition().distribution(), resolved.id(), resolved.kind());
    }

    /**
     * Resolves the environmental concentration at a position without exposing the mutable
     * chunk stock. The chunk stock is deliberately kept separate so HUD and fog rendering cannot
     * make stored aura appear to fluctuate with the environment.
     */
    public static AuraResult getSensedAura(Level level, BlockPos pos) {
        AuraResult resolved = getPositionAura(level, pos);
        AuraZone zone = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, resolved.source()).orElse(null);
        if (zone == null) return new AuraResult(Map.of(), resolved.auraKinds(), resolved.rules(),
                resolved.elementFitBonus(), resolved.elementConflictPenalty(), resolved.cultivateCondition(),
                resolved.distribution(), resolved.source(), resolved.sourceKind());
        Map<Holder<Resource>, AuraPool> pools = pools(zone, pos, level.getGameTime());
        return new AuraResult(pools, resolved.auraKinds(), resolved.rules(), resolved.elementFitBonus(),
                resolved.elementConflictPenalty(), resolved.cultivateCondition(), resolved.distribution(),
                resolved.source(), resolved.sourceKind());
    }

    private static AuraResult preview(Resolved resolved, Level level, BlockPos pos) {
        return new AuraResult(pools(resolved.definition(), pos, level.getGameTime()), resolved.definition().auraKinds(), resolved.definition().rules(),
                resolved.definition().elementFitBonus(), resolved.definition().elementConflictPenalty(),
                resolved.definition().cultivateCondition(), resolved.definition().distribution(), resolved.id(), resolved.kind());
    }

    /**
     * Consumes each requested resource pool atomically.
     */
    public static boolean consume(Level level, BlockPos pos, Map<Holder<Resource>, Double> costs) {
        if (costs.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0.0D)) return false;
        return level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK).consume(costs);
    }

    /**
     * Applies independent resource deltas. Missing resources are never implicitly created.
     */
    public static void change(Level level, BlockPos pos, Map<Holder<Resource>, Double> amounts) {
        if (amounts.values().stream().anyMatch(value -> !Double.isFinite(value))) return;
        level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK).change(amounts);
    }

    public static void initialize(AuraChunkAttachment chunk, Resolved resolved, BlockPos pos) {
        AuraZone zone = resolved.definition();
        Map<Holder<Resource>, AuraPool> pools = pools(zone, pos, 0L);
        chunk.initializeAuras(pools, zone.auraKinds());
        chunk.setTemplate(resolved.holder());
        chunk.setInitialized(true);
    }

    private static Resolved staticZone(Level level, BlockPos pos) {
        Identifier dimension = level.dimension().identifier();
        Identifier biome = HolderHelper.id(level.getBiome(pos));
        Resolved fallback = new Resolved(Optional.empty(), EMPTY_ZONE, SourceKind.CHUNK, Map.of());
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Resolved biomeResult = MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.AURA_ZONE)
                .filter(holder -> RegistryCodecs.matches(holder.value().biomes(), biomeRegistry, Registries.BIOME, biome))
                .findFirst().map(holder -> resolved(holder, SourceKind.BIOME)).orElse(fallback);
        return MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.AURA_ZONE)
                .filter(holder -> matchesDimension(level, holder.value(), dimension))
                .findFirst().map(holder -> resolved(holder, SourceKind.DIMENSION)).orElse(biomeResult);
    }

    /**
     * Dimension stems are a writable registry that is not synced to clients. The client can
     * still resolve direct dimension IDs from its level key, but only the server may expand a
     * dimension tag through the LEVEL_STEM registry.
     */
    private static boolean matchesDimension(Level level, AuraZone zone, Identifier dimension) {
        if (!(level instanceof ServerLevel server)) return RegistryCodecs.matchesKey(zone.dimensions(), dimension);
        Registry<LevelStem> registry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        return RegistryCodecs.matchesKey(zone.dimensions(), registry, dimension);
    }

    private static Optional<Resolved> customZone(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        return server.getData(MxtAttachments.AURA_WORLD).bestAt(pos)
                .flatMap(entry -> MxtDatapackRegistries.holder(MxtResourceKeys.AURA_ZONE, entry.getValue().zone())
                        .map(zone -> new Resolved(Optional.of(zone), zone.value(), SourceKind.CUSTOM, Map.of())));
    }

    private static Optional<Resolved> formationZone(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        return server.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet().stream()
                .filter(entry -> entry.getKey().distSqr(pos) <= entry.getValue().radius() * entry.getValue().radius())
                .max(Comparator.comparingDouble(entry -> -entry.getKey().distSqr(pos)))
                .flatMap(entry -> MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, entry.getValue().formation())
                        .flatMap(formation -> formation.auraZone().map(zone -> new Resolved(Optional.of(zone), zone.value(),
                                SourceKind.FORMATION, evaluateMaximumBonus(formation, FormulaContext.of(level))))));
    }

    /**
     * Highest active formation capacity bonus that intersects the given chunk.
     */
    @Deprecated
    public static double formationMaximumBonus(ServerLevel level, LevelChunk chunk) {
        double minX = chunk.getPos().getMinBlockX();
        double minZ = chunk.getPos().getMinBlockZ();
        double maxX = minX + 16.0D;
        double maxZ = minZ + 16.0D;
        return level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet().stream()
                .filter(entry -> distanceSquaredToChunk(entry.getKey(), minX, minZ, maxX, maxZ) <= entry.getValue().radius() * entry.getValue().radius())
                .mapToDouble(entry -> MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, entry.getValue().formation())
                        .map(formation -> evaluateMaximumBonus(formation, FormulaContext.of(level)).values().stream()
                                .mapToDouble(Double::doubleValue).sum()).orElse(0.0D))
                .max().orElse(0.0D);
    }

    private static double distanceSquaredToChunk(BlockPos origin, double minX, double minZ, double maxX, double maxZ) {
        double x = Math.clamp(origin.getX(), minX, maxX);
        double z = Math.clamp(origin.getZ(), minZ, maxZ);
        double deltaX = origin.getX() - x;
        double deltaZ = origin.getZ() - z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static Resolved resolved(Reference<AuraZone> holder, SourceKind kind) {
        return new Resolved(Optional.of(holder), holder.value(), kind, Map.of());
    }

    private static Map<Holder<Resource>, Double> evaluateMaximumBonus(Formation formation, FormulaContext context) {
        Map<Holder<Resource>, Double> values = new LinkedHashMap<>();
        formation.maxBonus().forEach((resource, provider) -> {
            double value = provider.evaluate(context);
            if (Double.isFinite(value) && value > 0.0D) values.put(resource, value);
        });
        return values;
    }

    private static void applyMaximumBonus(Map<Holder<Resource>, AuraPool> pools, Map<Holder<Resource>, Double> bonuses) {
        bonuses.forEach((resource, bonus) -> pools.computeIfPresent(resource, (ignored, pool) ->
                pool.maximum() == Double.POSITIVE_INFINITY ? pool : pool.withMaximum(pool.maximum() + bonus)));
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

    private static Map<Holder<Resource>, AuraPool> pools(AuraZone zone, BlockPos pos, long gameTime) {
        Map<Holder<Resource>, AuraPool> pools = new LinkedHashMap<>();
        double fluctuation = factor(zone, gameTime);
        zone.aura().forEach((resource, value) -> {
            double initial = Math.max(0.0D, (value.amount() + perlin(pos.getX(), pos.getZ(), zone.noise())) / 10.0D - 5.0D);
            pools.put(resource, new AuraPool(initial * fluctuation, value.max().resolve(initial), value.regenPerTick()));
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

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    public record Resolved(Optional<Holder<AuraZone>> holder, AuraZone definition, SourceKind kind,
                           Map<Holder<Resource>, Double> maxBonus) {
        public Identifier id() {
            return this.holder.map(HolderHelper::id).orElse(EMPTY);
        }
    }

    private static final AuraZone EMPTY_ZONE = new AuraZone(Map.of(), List.of(),
            List.of(), List.of(),
            Fluctuation.NONE, Rules.DEFAULT, AlwaysTrueCondition.INSTANCE, Distribution.EQUAL,
            0, 0, Noise.NONE, Optional.empty(), ClientRender.DEFAULT, ClientHud.NONE);
}
