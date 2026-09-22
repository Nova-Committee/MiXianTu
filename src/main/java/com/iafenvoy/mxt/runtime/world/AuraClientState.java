package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side copy of the server-resolved aura at the local player's position, with actual and environmental
 * concentration kept as separate snapshots. Both are keyed by the aura the server resolved them as, decoded
 * against the synced aura registry, so nothing here has to guess which aura a value belonged to.
 */
public final class AuraClientState {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");
    private static volatile Snapshot current = new Snapshot(EMPTY, Map.of(), Map.of());
    private static volatile Snapshot target = current;
    private static long lastNanos = System.nanoTime();

    private AuraClientState() {
    }

    public static synchronized Snapshot current() {
        long now = System.nanoTime();
        double elapsed = Math.max(0.0D, (now - lastNanos) / 1_000_000_000.0D);
        lastNanos = now;
        double factor = 1.0D - Math.exp(-elapsed / 0.25D);
        if (factor > 0.0D) current = Snapshot.interpolate(current, target, Math.min(1.0D, factor));
        return current;
    }

    public static void update(Identifier source, Map<Holder<Aura>, AuraPool> actual,
                              Map<Holder<Aura>, AuraPool> environment) {
        target = new Snapshot(source, sanitize(actual), sanitize(environment));
    }

    private static Map<Holder<Aura>, AuraPool> sanitize(Map<Holder<Aura>, AuraPool> aura) {
        Map<Holder<Aura>, AuraPool> sanitized = new LinkedHashMap<>();
        aura.forEach((holder, pool) -> {
            if (holder == null || pool == null) return;
            double amount = Double.isFinite(pool.amount()) ? Math.max(0.0D, pool.amount()) : 0.0D;
            double maximum = Double.isFinite(pool.maximum()) || pool.maximum() == Double.POSITIVE_INFINITY
                    ? Math.max(0.0D, pool.maximum()) : 0.0D;
            double regen = Double.isFinite(pool.regenPerTick()) ? pool.regenPerTick() : 0.0D;
            double supplied = Double.isFinite(pool.supplied()) ? Math.max(0.0D, pool.supplied()) : 0.0D;
            sanitized.put(holder, new AuraPool(amount, maximum, regen, supplied));
        });
        return Map.copyOf(sanitized);
    }

    public record Snapshot(Identifier source, Map<Holder<Aura>, AuraPool> actual,
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
            return this.environment.getOrDefault(aura, AuraPool.empty());
        }

        public AuraPool actualPool(Holder<Aura> aura) {
            return this.actual.getOrDefault(aura, AuraPool.empty());
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
                AuraPool start = from.getOrDefault(holder, AuraPool.empty());
                AuraPool end = to.getOrDefault(holder, AuraPool.empty());
                double amount = lerp(start.amount(), end.amount(), factor);
                double maximum = end.maximum() == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY
                        : lerp(start.maximum() == Double.POSITIVE_INFINITY ? end.maximum() : start.maximum(), end.maximum(), factor);
                double regen = lerp(start.regenPerTick(), end.regenPerTick(), factor);
                double supplied = lerp(start.supplied(), end.supplied(), factor);
                if (amount > 0.0D || maximum > 0.0D || regen != 0.0D)
                    values.put(holder, new AuraPool(amount, maximum, regen, supplied));
            });
            return Map.copyOf(values);
        }

        private static double lerp(double from, double to, double factor) {
            return from + (to - from) * factor;
        }
    }
}
