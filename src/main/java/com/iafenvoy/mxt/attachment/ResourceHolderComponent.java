package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-authoritative current values and resolved bounds. The bound snapshots are synchronised
 * with the attachment so clients never need to authoritatively evaluate a resource formula.
 */
public final class ResourceHolderComponent {
    public static final MapCodec<ResourceHolderComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.doubleMap(Resource.CODEC).fieldOf("values").forGetter(ResourceHolderComponent::values),
            CollectionCodecs.map(Resource.CODEC, Audit.CODEC).fieldOf("audit").forGetter(ResourceHolderComponent::audit)
    ).apply(i, ResourceHolderComponent::new));
    private final Object2DoubleMap<Holder<Resource>> values;
    private final Map<Holder<Resource>, Audit> audit;

    public ResourceHolderComponent() {
        this(Object2DoubleMaps.emptyMap(), Map.of());
    }

    private ResourceHolderComponent(Object2DoubleMap<Holder<Resource>> values, Map<Holder<Resource>, Audit> audit) {
        this.values = new Object2DoubleOpenHashMap<>(values);
        this.audit = new LinkedHashMap<>(audit);
    }

    public double get(Holder<Resource> resource) {
        return this.values.getDouble(resource);
    }

    public boolean contains(Holder<Resource> resource) {
        return this.values.containsKey(resource);
    }

    public void set(Holder<Resource> resource, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Resource value must be finite");
        this.values.put(resource, value);
        this.audit.putIfAbsent(resource, Audit.initial(value));
    }

    /**
     * Records a server-side state change with the resolved definition bounds and an auditable source.
     */
    public void set(Holder<Resource> resource, double value, double minSnapshot, double maxSnapshot, long changedAt, String source) {
        if (!Double.isFinite(value) || !Double.isFinite(minSnapshot) || !Double.isFinite(maxSnapshot)
                || minSnapshot > maxSnapshot || changedAt < -1L || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Invalid resource audit state");
        }
        this.values.put(resource, value);
        this.audit.put(resource, new Audit(minSnapshot, maxSnapshot, changedAt, source));
    }

    public boolean remove(Holder<Resource> resource) {
        this.audit.remove(resource);
        boolean b = this.values.containsKey(resource);
        this.values.removeDouble(resource);
        return b;
    }

    public Object2DoubleMap<Holder<Resource>> values() {
        return this.values;
    }

    public Map<Holder<Resource>, Audit> audit() {
        return this.audit;
    }

    public Audit audit(Holder<Resource> resource) {
        return this.audit.getOrDefault(resource, Audit.initial(this.get(resource)));
    }

    /**
     * Captures values and resolved bounds together for transactional rollback.
     */
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
    public void restore(Map<Holder<Resource>, Double> snapshot) {
        this.values.clear();
        this.audit.clear();
        snapshot.forEach(this::set);
    }

    public record Snapshot(Map<Holder<Resource>, Double> values, Map<Holder<Resource>, Audit> audit) {
        public Snapshot {
            values = new LinkedHashMap<>(values);
            audit = new LinkedHashMap<>(audit);
        }
    }

    public record Audit(double minSnapshot, double maxSnapshot, long lastChangedTick, String source) {
        public static final Codec<Audit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("min_snapshot").forGetter(Audit::minSnapshot),
                Codec.DOUBLE.fieldOf("max_snapshot").forGetter(Audit::maxSnapshot),
                Codec.LONG.fieldOf("last_changed_tick").forGetter(Audit::lastChangedTick),
                Codec.STRING.fieldOf("source").forGetter(Audit::source)
        ).apply(i, Audit::new));

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
