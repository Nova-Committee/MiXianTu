package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-authoritative current values and resolved bounds. The bound snapshots are synchronised
 * with the attachment so clients never need to authoritatively evaluate a resource formula.
 */
public final class ResourceHolderData {
    public static final MapCodec<ResourceHolderData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("values").forGetter(ResourceHolderData::values),
            Codec.unboundedMap(Identifier.CODEC, Audit.CODEC).fieldOf("audit").forGetter(ResourceHolderData::audit)
    ).apply(instance, ResourceHolderData::decode));
    public static final Codec<ResourceHolderData> CODEC = MAP_CODEC.codec();
    private final Map<Identifier, Double> values;
    private final Map<Identifier, Audit> audit;

    public ResourceHolderData() {
        this(Map.of(), Map.of());
    }

    private ResourceHolderData(Map<Identifier, Double> values, Map<Identifier, Audit> audit) {
        this.values = new LinkedHashMap<>();
        this.audit = new LinkedHashMap<>(audit);
        values.forEach((id, value) -> {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Resource value must be finite");
            if (!this.audit.containsKey(id)) throw new IllegalArgumentException("Resource audit is required for " + id);
            this.values.put(id, value);
        });
    }

    private static ResourceHolderData decode(Map<Identifier, Double> values, Map<Identifier, Audit> audit) {
        return new ResourceHolderData(values, audit);
    }

    public double get(Identifier resource) {
        return this.values.getOrDefault(resource, 0.0D);
    }

    public boolean contains(Identifier resource) {
        return this.values.containsKey(resource);
    }

    public void set(Identifier resource, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Resource value must be finite");
        this.values.put(resource, value);
        this.audit.putIfAbsent(resource, Audit.initial(value));
    }

    /**
     * Records a server-side state change with the resolved definition bounds and an auditable source.
     */
    public void set(Identifier resource, double value, double minSnapshot, double maxSnapshot, long changedAt, String source) {
        if (!Double.isFinite(value) || !Double.isFinite(minSnapshot) || !Double.isFinite(maxSnapshot)
                || minSnapshot > maxSnapshot || changedAt < -1L || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Invalid resource audit state");
        }
        this.values.put(resource, value);
        this.audit.put(resource, new Audit(minSnapshot, maxSnapshot, changedAt, source));
    }

    public boolean remove(Identifier resource) {
        this.audit.remove(resource);
        return this.values.remove(resource) != null;
    }

    public Map<Identifier, Double> values() {
        return Map.copyOf(this.values);
    }

    public Map<Identifier, Audit> audit() {
        return Map.copyOf(this.audit);
    }

    public Audit audit(Identifier resource) {
        return this.audit.getOrDefault(resource, Audit.initial(this.get(resource)));
    }

    /** Captures values and resolved bounds together for transactional rollback. */
    public Snapshot snapshot() {
        return new Snapshot(this.values, this.audit);
    }

    public void restore(Snapshot snapshot) {
        this.values.clear();
        this.audit.clear();
        this.values.putAll(snapshot.values());
        this.audit.putAll(snapshot.audit());
    }

    /**
     * Restores legacy value-only state. Prefer {@link #snapshot()} and {@link #restore(Snapshot)}
     * for transactions so resolved client-visible bounds are preserved.
     */
    public void restore(Map<Identifier, Double> snapshot) {
        this.values.clear();
        this.audit.clear();
        snapshot.forEach(this::set);
    }

    public record Snapshot(Map<Identifier, Double> values, Map<Identifier, Audit> audit) {
        public Snapshot {
            values = Map.copyOf(values);
            audit = Map.copyOf(audit);
        }
    }

    public record Audit(double minSnapshot, double maxSnapshot, long lastChangedTick, String source) {
        public static final Codec<Audit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("min_snapshot").forGetter(Audit::minSnapshot),
                Codec.DOUBLE.fieldOf("max_snapshot").forGetter(Audit::maxSnapshot),
                Codec.LONG.fieldOf("last_changed_tick").forGetter(Audit::lastChangedTick),
                Codec.STRING.fieldOf("source").forGetter(Audit::source)
        ).apply(instance, Audit::new));

        public Audit {
            if (!Double.isFinite(minSnapshot) || !Double.isFinite(maxSnapshot) || minSnapshot > maxSnapshot
                    || lastChangedTick < -1L || source == null || source.isBlank())
                throw new IllegalArgumentException("Invalid resource audit");
        }

        private static Audit initial(double value) {
            return new Audit(value, value, -1L, "initial");
        }
    }
}
