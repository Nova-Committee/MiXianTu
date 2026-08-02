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
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(AuraChunkData::initialized),
            Identifier.CODEC.optionalFieldOf("template", Identifier.fromNamespaceAndPath("mxt", "empty")).forGetter(AuraChunkData::template),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("element_bias", Map.of()).forGetter(AuraChunkData::elementBias),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(AuraChunkData::environmentTags),
            Identifier.CODEC.listOf().optionalFieldOf("template_environment_tags", List.of()).forGetter(AuraChunkData::templateEnvironmentTags),
            Codec.DOUBLE.optionalFieldOf("block_aura", 0.0D).forGetter(AuraChunkData::blockAura),
            Codec.DOUBLE.optionalFieldOf("block_regen_per_tick", 0.0D).forGetter(AuraChunkData::blockRegenPerTick),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("block_element_aura", Map.of()).forGetter(AuraChunkData::blockElementAura),
            Identifier.CODEC.listOf().optionalFieldOf("block_environment_tags", List.of()).forGetter(AuraChunkData::blockEnvironmentTags)
    ).apply(instance, AuraChunkData::decode));
    public static final Codec<AuraChunkData> CODEC = MAP_CODEC.codec();
    private double concentration;
    private double regenPerTick;
    private boolean initialized;
    private Identifier template;
    private final Map<Identifier, Double> elementBias;
    private final Set<Identifier> environmentTags;
    private final Set<Identifier> templateEnvironmentTags;
    private double blockAura;
    private double blockRegenPerTick;
    private final Map<Identifier, Double> blockElementAura;
    private final Set<Identifier> blockEnvironmentTags;

    public AuraChunkData() {
        this(0.0D, 0.0D, false, Identifier.fromNamespaceAndPath("mxt", "empty"), Map.of(), List.of(), List.of(), 0.0D, 0.0D, Map.of(), List.of());
    }

    private AuraChunkData(double concentration, double regenPerTick, boolean initialized, Identifier template, Map<Identifier, Double> elementBias, List<Identifier> environmentTags, List<Identifier> templateEnvironmentTags,
                          double blockAura, double blockRegenPerTick, Map<Identifier, Double> blockElementAura, List<Identifier> blockEnvironmentTags) {
        this.concentration = finite(concentration);
        this.regenPerTick = finite(regenPerTick);
        this.initialized = initialized;
        this.template = template;
        this.elementBias = new LinkedHashMap<>();
        elementBias.forEach((key, value) -> this.elementBias.put(key, finite(value)));
        this.environmentTags = new LinkedHashSet<>(environmentTags);
        this.templateEnvironmentTags = new LinkedHashSet<>(templateEnvironmentTags.isEmpty() ? environmentTags : templateEnvironmentTags);
        this.blockAura = finite(blockAura);
        this.blockRegenPerTick = finite(blockRegenPerTick);
        this.blockElementAura = new LinkedHashMap<>();
        blockElementAura.forEach((key, value) -> this.blockElementAura.put(key, finite(value)));
        this.blockEnvironmentTags = new LinkedHashSet<>(blockEnvironmentTags);
    }

    private static AuraChunkData decode(double concentration, double regenPerTick, boolean initialized, Identifier template, Map<Identifier, Double> elementBias, List<Identifier> environmentTags, List<Identifier> templateEnvironmentTags,
                                        double blockAura, double blockRegenPerTick, Map<Identifier, Double> blockElementAura, List<Identifier> blockEnvironmentTags) {
        return new AuraChunkData(concentration, regenPerTick, initialized, template, elementBias, environmentTags, templateEnvironmentTags, blockAura, blockRegenPerTick, blockElementAura, blockEnvironmentTags);
    }

    public double concentration() {
        return this.concentration;
    }

    public double regenPerTick() {
        return this.regenPerTick;
    }

    public boolean initialized() {
        return this.initialized;
    }

    public void setInitialized(boolean value) {
        this.initialized = value;
    }

    public Identifier template() {
        return this.template;
    }

    public void setTemplate(Identifier value) {
        this.template = Objects.requireNonNull(value, "template");
    }

    public Map<Identifier, Double> elementBias() {
        return Map.copyOf(this.elementBias);
    }

    public List<Identifier> environmentTags() {
        return List.copyOf(this.environmentTags);
    }

    public List<Identifier> templateEnvironmentTags() {
        return List.copyOf(this.templateEnvironmentTags);
    }

    public double blockAura() {
        return this.blockAura;
    }

    public double blockRegenPerTick() {
        return this.blockRegenPerTick;
    }

    public Map<Identifier, Double> blockElementAura() {
        return Map.copyOf(this.blockElementAura);
    }

    public List<Identifier> blockEnvironmentTags() {
        return List.copyOf(this.blockEnvironmentTags);
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
        this.templateEnvironmentTags.clear();
        this.templateEnvironmentTags.addAll(values);
        this.refreshEnvironmentTags();
    }

    /**
     * Replaces the cached contribution from blocks while preserving consumed aura stock.
     */
    public void setBlockContribution(double aura, double regen, Map<Identifier, Double> elements, Collection<Identifier> tags) {
        double newAura = finite(aura);
        double newRegen = finite(regen);
        this.concentration = Math.max(0.0D, this.concentration + newAura - this.blockAura);
        this.regenPerTick = finite(this.regenPerTick + newRegen - this.blockRegenPerTick);
        this.blockElementAura.forEach((key, value) -> this.elementBias.compute(key, (ignored, current) -> {
            double next = (current == null ? 0.0D : current) - value;
            return Math.abs(next) < 1.0E-9D ? null : next;
        }));
        this.blockElementAura.clear();
        elements.forEach((key, value) -> {
            double valid = finite(value);
            this.blockElementAura.put(key, valid);
            this.elementBias.merge(key, valid, Double::sum);
        });
        this.blockAura = newAura;
        this.blockRegenPerTick = newRegen;
        this.blockEnvironmentTags.clear();
        this.blockEnvironmentTags.addAll(tags);
        this.refreshEnvironmentTags();
    }

    public boolean hasEnvironmentTags(Collection<Identifier> values) {
        return this.environmentTags.containsAll(values);
    }

    private void refreshEnvironmentTags() {
        this.environmentTags.clear();
        this.environmentTags.addAll(this.templateEnvironmentTags);
        this.environmentTags.addAll(this.blockEnvironmentTags);
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
