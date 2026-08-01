package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Chunk-level aura concentration and element offsets, changed by formations and world features.
 */
public final class AuraChunkData {
    public static final MapCodec<AuraChunkData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("concentration", 0.0D).forGetter(AuraChunkData::concentration),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraChunkData::regenPerTick),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("element_bias", Map.of()).forGetter(AuraChunkData::elementBias),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(AuraChunkData::environmentTags)
    ).apply(instance, AuraChunkData::decode));
    public static final Codec<AuraChunkData> CODEC = MAP_CODEC.codec();
    private double concentration;
    private double regenPerTick;
    private final Map<Identifier, Double> elementBias;
    private final Set<Identifier> environmentTags;

    public AuraChunkData() {
        this(0.0D, 0.0D, Map.of(), List.of());
    }

    private AuraChunkData(double concentration, double regenPerTick, Map<Identifier, Double> elementBias, List<Identifier> environmentTags) {
        this.concentration = finite(concentration);
        this.regenPerTick = finite(regenPerTick);
        this.elementBias = new LinkedHashMap<>();
        elementBias.forEach((key, value) -> this.elementBias.put(key, finite(value)));
        this.environmentTags = new LinkedHashSet<>(environmentTags);
    }

    private static AuraChunkData decode(double concentration, double regenPerTick, Map<Identifier, Double> elementBias, List<Identifier> environmentTags) {
        return new AuraChunkData(concentration, regenPerTick, elementBias, environmentTags);
    }

    public double concentration() {
        return this.concentration;
    }

    public double regenPerTick() {
        return this.regenPerTick;
    }

    public Map<Identifier, Double> elementBias() {
        return Map.copyOf(this.elementBias);
    }

    public List<Identifier> environmentTags() {
        return List.copyOf(this.environmentTags);
    }

    public void setConcentration(double value) {
        this.concentration = finite(value);
    }

    public void setRegenPerTick(double value) {
        this.regenPerTick = finite(value);
    }

    public void setElementBias(Identifier element, double value) {
        this.elementBias.put(element, finite(value));
    }

    public void setEnvironmentTags(Collection<Identifier> values) {
        this.environmentTags.clear();
        this.environmentTags.addAll(values);
    }

    public boolean hasEnvironmentTags(Collection<Identifier> values) {
        return this.environmentTags.containsAll(values);
    }

    /**
     * Applies a bounded number of elapsed server ticks; the loaded-chunk ticker owns normal scheduling.
     */
    public void regenerate(long elapsedTicks) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        this.setConcentration(Math.max(0.0D, this.concentration + this.regenPerTick * elapsedTicks));
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Aura values must be finite");
        return value;
    }
}
