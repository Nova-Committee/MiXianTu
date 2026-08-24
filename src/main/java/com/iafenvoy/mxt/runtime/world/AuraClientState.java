package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side copy of the server-resolved aura at the local player's position. Stored chunk
 * inventory and sensed environmental concentration are kept as separate snapshots.
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

    public static void update(Identifier source, Map<Identifier, AuraPool> stored,
                              Map<Identifier, AuraPool> sensed) {
        target = new Snapshot(source, sanitize(stored), sanitize(sensed));
    }

    private static Map<Identifier, AuraPool> sanitize(Map<Identifier, AuraPool> aura) {
        Map<Identifier, AuraPool> sanitized = new LinkedHashMap<>();
        aura.forEach((id, pool) -> {
            if (id == null || pool == null) return;
            double amount = Double.isFinite(pool.amount()) ? Math.max(0.0D, pool.amount()) : 0.0D;
            double maximum = Double.isFinite(pool.maximum()) || pool.maximum() == Double.POSITIVE_INFINITY
                    ? Math.max(0.0D, pool.maximum()) : 0.0D;
            double regen = Double.isFinite(pool.regenPerTick()) ? pool.regenPerTick() : 0.0D;
            sanitized.put(id, new AuraPool(amount, maximum, regen));
        });
        return Map.copyOf(sanitized);
    }

    public record Snapshot(Identifier source, Map<Identifier, AuraPool> stored,
                           Map<Identifier, AuraPool> sensed) {
        public double storedConcentration() {
            return this.stored.values().stream().mapToDouble(AuraPool::amount).sum();
        }

        public double storedMaximum() {
            return this.stored.values().stream().mapToDouble(AuraPool::maximum).sum();
        }

        public double sensedConcentration() {
            return this.sensed.values().stream().mapToDouble(AuraPool::amount).sum();
        }

        public double sensedMaximum() {
            return this.sensed.values().stream().mapToDouble(AuraPool::maximum).sum();
        }

        public AuraPool sensedPool(Identifier id) {
            return this.sensed.getOrDefault(id, new AuraPool(0.0D, 0.0D, 0.0D));
        }

        private static Snapshot interpolate(Snapshot from, Snapshot to, double factor) {
            return new Snapshot(to.source, interpolateMap(from.stored, to.stored, factor),
                    interpolateMap(from.sensed, to.sensed, factor));
        }

        private static Map<Identifier, AuraPool> interpolateMap(Map<Identifier, AuraPool> from,
                                                                Map<Identifier, AuraPool> to, double factor) {
            Map<Identifier, AuraPool> values = new LinkedHashMap<>();
            Set<Identifier> ids = new HashSet<>(from.keySet());
            ids.addAll(to.keySet());
            ids.forEach(id -> {
                AuraPool start = from.getOrDefault(id, new AuraPool(0.0D, 0.0D, 0.0D));
                AuraPool end = to.getOrDefault(id, new AuraPool(0.0D, 0.0D, 0.0D));
                double amount = lerp(start.amount(), end.amount(), factor);
                double maximum = end.maximum() == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY
                        : lerp(start.maximum() == Double.POSITIVE_INFINITY ? end.maximum() : start.maximum(), end.maximum(), factor);
                double regen = lerp(start.regenPerTick(), end.regenPerTick(), factor);
                if (amount > 0.0D || maximum > 0.0D || regen != 0.0D)
                    values.put(id, new AuraPool(amount, maximum, regen));
            });
            return Map.copyOf(values);
        }

        private static double lerp(double from, double to, double factor) {
            return from + (to - from) * factor;
        }
    }
}
