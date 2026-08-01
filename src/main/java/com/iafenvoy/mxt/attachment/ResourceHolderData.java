package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-authoritative current values; resource bounds and regen stay in the datapack definition.
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
     * Records a server-side state change with the resolved definition maximum and an auditable source.
     */
    public void set(Identifier resource, double value, double maxSnapshot, long changedAt, String source) {
        if (!Double.isFinite(value) || !Double.isFinite(maxSnapshot) || changedAt < -1L || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Invalid resource audit state");
        }
        this.values.put(resource, value);
        this.audit.put(resource, new Audit(maxSnapshot, changedAt, source));
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

    public void restore(Map<Identifier, Double> snapshot) {
        this.values.clear();
        this.audit.clear();
        snapshot.forEach(this::set);
    }

    public record Audit(double maxSnapshot, long lastChangedTick, String source) {
        public static final Codec<Audit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("max_snapshot").forGetter(Audit::maxSnapshot),
                Codec.LONG.fieldOf("last_changed_tick").forGetter(Audit::lastChangedTick),
                Codec.STRING.fieldOf("source").forGetter(Audit::source)
        ).apply(instance, Audit::new));

        public Audit {
            if (!Double.isFinite(maxSnapshot) || lastChangedTick < -1L || source == null || source.isBlank())
                throw new IllegalArgumentException("Invalid resource audit");
        }

        private static Audit initial(double value) {
            return new Audit(value, -1L, "initial");
        }
    }
}
