package com.iafenvoy.mxt.runtime.world;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientHud;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientRender;
import com.iafenvoy.mxt.data.aura.AuraZone.CycleType;
import com.iafenvoy.mxt.data.aura.AuraZone.Fluctuation;
import com.iafenvoy.mxt.data.aura.AuraZone.Noise;
import com.iafenvoy.mxt.data.aura.AuraZone.Rules;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;

/**
 * Central aura resolver. Its precedence is biome, dimension, custom area, then active formation.
 */
public final class AuraService {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");

    private AuraService() {
    }

    public static AuraResult getPositionAura(Level level, BlockPos pos) {
        AuraChunkData chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        Resolved staticResolved = staticZone(level, pos);
        if (!chunk.initialized() || !chunk.template().equals(staticResolved.holder()))
            initialize(chunk, staticResolved, pos);
        Resolved resolved = staticResolved;
        Resolved lower = resolved;
        resolved = customZone(level, pos).orElse(resolved);
        Resolved formation = formationZone(level, pos).orElse(null);
        if (formation != null && level instanceof ServerLevel server
                && !NeoForge.EVENT_BUS.post(new AuraZoneEvent.Override(server, pos, preview(lower, level), formation.id())).isCanceled()) {
            resolved = formation;
        }
        double staticFactor = factor(staticResolved.definition(), level.getGameTime());
        double fluctuation = factor(resolved.definition(), level.getGameTime());
        double concentration = chunk.concentration() * staticFactor;
        if (resolved.kind() == SourceKind.CUSTOM || resolved.kind() == SourceKind.FORMATION) {
            // Overlay only the template delta. The backing chunk stock remains authoritative and is consumed normally.
            double staticBase = naturalAura(staticResolved.definition(), pos) * staticFactor;
            concentration = Math.max(0.0D, concentration + resolved.definition().baseAura() * fluctuation - staticBase);
        }
        Map<Holder<Element>, Double> elements = resolved.kind() == SourceKind.BIOME || resolved.kind() == SourceKind.DIMENSION || resolved.kind() == SourceKind.CHUNK
                ? chunk.elementBias() : resolved.definition().elementAura();
        return new AuraResult(concentration, resolved.definition().regenPerTick(), elements, resolved.definition().auraKinds(),
                resolved.definition().rules(), resolved.definition().elementFitBonus(), resolved.definition().elementConflictPenalty(), resolved.id(), resolved.kind());
    }

    private static AuraResult preview(Resolved resolved, Level level) {
        return new AuraResult(resolved.definition().baseAura() * factor(resolved.definition(), level.getGameTime()), resolved.definition().regenPerTick(), resolved.definition().elementAura(), resolved.definition().auraKinds(), resolved.definition().rules(), resolved.definition().elementFitBonus(), resolved.definition().elementConflictPenalty(), resolved.id(), resolved.kind());
    }

    public static boolean consume(Level level, BlockPos pos, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) return false;
        AuraChunkData chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        if (getPositionAura(level, pos).concentration() < amount) return false;
        chunk.setConcentration(Math.max(0.0D, chunk.concentration() - amount));
        return true;
    }

    public static void initialize(AuraChunkData chunk, Resolved resolved, BlockPos pos) {
        AuraZone zone = resolved.definition();
        double value = naturalAura(zone, pos);
        chunk.setConcentration(value + chunk.blockAura());
        chunk.setRegenPerTick(zone.regenPerTick() + chunk.blockRegenPerTick());
        Map<Holder<Element>, Double> elements = new LinkedHashMap<>(zone.elementAura());
        chunk.blockElementAura().forEach((element, amount) -> elements.merge(element, amount, Double::sum));
        chunk.elementBias().keySet().forEach(element -> chunk.setElementBias(element, 0.0D));
        elements.forEach(chunk::setElementBias);
        chunk.setAuraKinds(zone.auraKinds());
        chunk.setTemplate(resolved.holder());
        chunk.setInitialized(true);
    }

    private static Resolved staticZone(Level level, BlockPos pos) {
        Registry<AuraZone> zones = level.registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE);
        Identifier dimension = level.dimension().identifier();
        Identifier biome = level.getBiome(pos).unwrapKey().map(ResourceKey::identifier).orElse(EMPTY);
        Resolved fallback = new Resolved(Optional.empty(), EMPTY_ZONE, SourceKind.CHUNK);
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
                        .map(zone -> new Resolved(Optional.of(zone), zone.value(), SourceKind.CUSTOM)));
    }

    private static Optional<Resolved> formationZone(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        return server.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet().stream()
                .filter(entry -> entry.getKey().distSqr(pos) <= entry.getValue().radius() * entry.getValue().radius())
                .max(Comparator.comparingDouble(entry -> -entry.getKey().distSqr(pos)))
                .flatMap(entry -> MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, entry.getValue().formation())
                        .flatMap(Formation::auraZone)
                        .map(zone -> new Resolved(Optional.of(zone), zone.value(), SourceKind.FORMATION)));
    }

    private static Resolved resolved(Reference<AuraZone> holder, SourceKind kind) {
        return new Resolved(Optional.of(holder), holder.value(), kind);
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

    /**
     * Natural world aura is intentionally sparse; block contributions are added separately.
     */
    private static double naturalAura(AuraZone zone, BlockPos pos) {
        return Math.max(0.0D, (zone.baseAura() + perlin(pos.getX(), pos.getZ(), zone.noise())) / 10.0D - 5.0D);
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

    public record Resolved(Optional<Holder<AuraZone>> holder, AuraZone definition, SourceKind kind) {
        public Identifier id() {
            return this.holder.map(HolderHelper::id).orElse(EMPTY);
        }
    }

    private static final AuraZone EMPTY_ZONE = new AuraZone(0, 0, Object2DoubleMaps.emptyMap(), List.of(),
            List.of(), List.of(),
            Fluctuation.NONE, Rules.DEFAULT, 0, 0, Noise.NONE, Optional.empty(), ClientRender.DEFAULT, ClientHud.NONE);
}
