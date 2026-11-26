package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.util.MiscUtil;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Client-side copy of the server-resolved aura at the local player's position, with actual and environmental
 * concentration kept as separate snapshots. Both are keyed by the aura the server resolved them as, decoded
 * against the synced aura registry, so nothing here has to guess which aura a value belonged to.
 */
public final class AuraClientState {
    private static final AuraPool EMPTY_POOL = AuraPool.empty().snapshot();
    private static final Snapshot EMPTY = new Snapshot(Optional.empty(), Map.of(), Map.of());
    private static Snapshot current = EMPTY;
    private static volatile @Nullable Snapshot target;
    private static long lastNanos = System.nanoTime();

    private AuraClientState() {
    }

    public static synchronized Snapshot current() {
        Snapshot sample = target;
        if (sample == null) return EMPTY;
        long now = System.nanoTime();
        double elapsed = Math.max(0.0D, (now - lastNanos) / 1_000_000_000.0D);
        lastNanos = now;
        double factor = 1.0D - Math.exp(-elapsed / 0.25D);
        if (factor > 0.0D) current = Snapshot.interpolate(current, sample, Math.min(1.0D, factor));
        return current;
    }

    /** Returns the last server sample for the local player, or empty before a sample arrives. */
    public static Optional<Snapshot> latest() {
        return Optional.ofNullable(target);
    }

    public static synchronized void clear() {
        target = null;
        current = EMPTY;
        lastNanos = System.nanoTime();
    }

    // An absent source is a real answer: no zone covers the player, so the previous one has to stop being drawn.
    public static synchronized void update(Optional<Holder<AuraZone>> source, Map<Holder<Aura>, AuraPool> actual, Map<Holder<Aura>, AuraPool> environment) {
        if (target == null) lastNanos = System.nanoTime();
        target = new Snapshot(source, sanitize(actual), sanitize(environment));
    }

    private static Map<Holder<Aura>, AuraPool> sanitize(Map<Holder<Aura>, AuraPool> aura) {
        Map<Holder<Aura>, AuraPool> sanitized = new LinkedHashMap<>();
        aura.forEach((holder, pool) -> {
            if (holder == null || pool == null) return;
            double amount = MiscUtil.positive(pool.amount());
            double maximum = Double.isFinite(pool.maximum()) || pool.maximum() == Double.POSITIVE_INFINITY
                    ? Math.max(0.0D, pool.maximum()) : 0.0D;
            double regen = Double.isFinite(pool.regenPerTick()) ? pool.regenPerTick() : 0.0D;
            double supplied = MiscUtil.positive(pool.supplied());
            sanitized.put(holder, AuraPool.readOnly(amount, maximum, regen, supplied));
        });
        return Map.copyOf(sanitized);
    }

    public record Snapshot(Optional<Holder<AuraZone>> source, Map<Holder<Aura>, AuraPool> actual,
                           Map<Holder<Aura>, AuraPool> environment) {
        public double actualConcentration() {
            return this.actual.values().stream().mapToDouble(AuraPool::amount).sum();
        }

        public double actualMaximum() {
            return this.actual.values().stream().mapToDouble(AuraPool::maximum).sum();
        }

        public double environmentConcentration() {
            return this.environment.values().stream().mapToDouble(AuraPool::amount).sum();
        }

        public double environmentMaximum() {
            return this.environment.values().stream().mapToDouble(AuraPool::maximum).sum();
        }

        public AuraPool environmentPool(Holder<Aura> aura) {
            return this.environment.getOrDefault(aura, EMPTY_POOL);
        }

        public AuraPool actualPool(Holder<Aura> aura) {
            return this.actual.getOrDefault(aura, EMPTY_POOL);
        }

        public AuraPool environmentPoolForResource(Holder<Resource> resource) {
            return poolForResource(this.environment, resource);
        }

        public AuraPool actualPoolForResource(Holder<Resource> resource) {
            return poolForResource(this.actual, resource);
        }

        // Resolve from the packet's holders because the shared registry cache may be cleared while joining a world.
        private static AuraPool poolForResource(Map<Holder<Aura>, AuraPool> pools, Holder<Resource> resource) {
            for (Map.Entry<Holder<Aura>, AuraPool> entry : pools.entrySet())
                if (entry.getKey().value().resource().equals(resource)) return entry.getValue();
            return EMPTY_POOL;
        }

        private static Snapshot interpolate(Snapshot from, Snapshot to, double factor) {
            return new Snapshot(to.source, interpolateMap(from.actual, to.actual, factor),
                    interpolateMap(from.environment, to.environment, factor));
        }

        private static Map<Holder<Aura>, AuraPool> interpolateMap(Map<Holder<Aura>, AuraPool> from,
                                                                  Map<Holder<Aura>, AuraPool> to, double factor) {
            Map<Holder<Aura>, AuraPool> values = new LinkedHashMap<>();
            Set<Holder<Aura>> holders = new HashSet<>(from.keySet());
            holders.addAll(to.keySet());
            holders.forEach(holder -> {
                AuraPool start = from.getOrDefault(holder, EMPTY_POOL);
                AuraPool end = to.getOrDefault(holder, EMPTY_POOL);
                double amount = lerp(start.amount(), end.amount(), factor);
                double maximum = end.maximum() == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY
                        : lerp(start.maximum() == Double.POSITIVE_INFINITY ? end.maximum() : start.maximum(), end.maximum(), factor);
                double regen = lerp(start.regenPerTick(), end.regenPerTick(), factor);
                double supplied = lerp(start.supplied(), end.supplied(), factor);
                if (amount > 0.0D || maximum > 0.0D || regen != 0.0D)
                    values.put(holder, AuraPool.readOnly(amount, maximum, regen, supplied));
            });
            return Map.copyOf(values);
        }

        private static double lerp(double from, double to, double factor) {
            return from + (to - from) * factor;
        }
    }
}
