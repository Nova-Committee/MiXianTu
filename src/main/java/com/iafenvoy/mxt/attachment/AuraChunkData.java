package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Chunk-level aura concentration and element offsets, changed by formations and world features.
 */
public final class AuraChunkData {
    public static final MapCodec<AuraChunkData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("concentration", 0.0D).forGetter(AuraChunkData::concentration),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraChunkData::regenPerTick),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(AuraChunkData::initialized),
            AuraZone.CODEC.optionalFieldOf("template").forGetter(AuraChunkData::template),
            CollectionCodecs.doubleMap(Element.CODEC).optionalFieldOf("element_bias", Object2DoubleMaps.emptyMap()).forGetter(AuraChunkData::elementBias),
            Identifier.CODEC.listOf().<Set<Identifier>>xmap(LinkedHashSet::new, ArrayList::new).optionalFieldOf("aura_kinds", Set.of()).forGetter(AuraChunkData::auraKinds),
            Identifier.CODEC.listOf().<Set<Identifier>>xmap(LinkedHashSet::new, ArrayList::new).optionalFieldOf("template_aura_kinds", Set.of()).forGetter(AuraChunkData::templateAuraKinds),
            Codec.DOUBLE.optionalFieldOf("block_aura", 0.0D).forGetter(AuraChunkData::blockAura),
            Codec.DOUBLE.optionalFieldOf("block_regen_per_tick", 0.0D).forGetter(AuraChunkData::blockRegenPerTick),
            CollectionCodecs.doubleMap(Element.CODEC).optionalFieldOf("block_element_aura", Object2DoubleMaps.emptyMap()).forGetter(AuraChunkData::blockElementAura),
            Identifier.CODEC.listOf().<Set<Identifier>>xmap(LinkedHashSet::new, ArrayList::new).optionalFieldOf("block_aura_kinds", Set.of()).forGetter(AuraChunkData::blockAuraKinds)
    ).apply(i, AuraChunkData::new));
    private double concentration;
    private double regenPerTick;
    private boolean initialized;
    private Optional<Holder<AuraZone>> template;
    private final Object2DoubleMap<Holder<Element>> elementBias;
    private final Set<Identifier> auraKinds;
    private final Set<Identifier> templateAuraKinds;
    private double blockAura;
    private double blockRegenPerTick;
    private final Object2DoubleMap<Holder<Element>> blockElementAura;
    private final Set<Identifier> blockAuraKinds;

    public AuraChunkData() {
        this(0.0D, 0.0D, false, Optional.empty(), Object2DoubleMaps.emptyMap(), Set.of(), Set.of(), 0.0D, 0.0D, Object2DoubleMaps.emptyMap(), Set.of());
    }

    private AuraChunkData(double concentration, double regenPerTick, boolean initialized, Optional<Holder<AuraZone>> template, Object2DoubleMap<Holder<Element>> elementBias, Set<Identifier> auraKinds, Set<Identifier> templateAuraKinds,
                          double blockAura, double blockRegenPerTick, Object2DoubleMap<Holder<Element>> blockElementAura, Set<Identifier> blockAuraKinds) {
        this.concentration = finite(concentration);
        this.regenPerTick = finite(regenPerTick);
        this.initialized = initialized;
        this.template = template;
        this.elementBias = new Object2DoubleOpenHashMap<>();
        elementBias.forEach((key, value) -> this.elementBias.put(key, finite(value)));
        this.auraKinds = new LinkedHashSet<>(auraKinds);
        this.templateAuraKinds = new LinkedHashSet<>(templateAuraKinds.isEmpty() ? auraKinds : templateAuraKinds);
        this.blockAura = finite(blockAura);
        this.blockRegenPerTick = finite(blockRegenPerTick);
        this.blockElementAura = new Object2DoubleOpenHashMap<>();
        blockElementAura.forEach((key, value) -> this.blockElementAura.put(key, finite(value)));
        this.blockAuraKinds = new LinkedHashSet<>(blockAuraKinds);
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

    public Optional<Holder<AuraZone>> template() {
        return this.template;
    }

    public void setTemplate(Optional<Holder<AuraZone>> value) {
        this.template = value;
    }

    public Object2DoubleMap<Holder<Element>> elementBias() {
        return this.elementBias;
    }

    public Set<Identifier> auraKinds() {
        return this.auraKinds;
    }

    public Set<Identifier> templateAuraKinds() {
        return this.templateAuraKinds;
    }

    public double blockAura() {
        return this.blockAura;
    }

    public double blockRegenPerTick() {
        return this.blockRegenPerTick;
    }

    public Object2DoubleMap<Holder<Element>> blockElementAura() {
        return this.blockElementAura;
    }

    public Set<Identifier> blockAuraKinds() {
        return this.blockAuraKinds;
    }

    public void setConcentration(double value) {
        this.concentration = finite(value);
    }

    public void setRegenPerTick(double value) {
        this.regenPerTick = finite(value);
    }

    public void setElementBias(Holder<Element> element, double value) {
        this.elementBias.put(element, finite(value));
    }

    public void setAuraKinds(Collection<Identifier> values) {
        this.templateAuraKinds.clear();
        this.templateAuraKinds.addAll(values);
        this.refreshAuraKinds();
    }

    /**
     * Replaces the cached contribution from blocks while preserving consumed aura stock.
     */
    public void setBlockContribution(double aura, double regen, Map<Holder<Element>, Double> elements, Collection<Identifier> tags) {
        double newAura = finite(aura);
        double newRegen = finite(regen);
        this.concentration = Math.max(0.0D, this.concentration + newAura - this.blockAura);
        this.regenPerTick = finite(this.regenPerTick + newRegen - this.blockRegenPerTick);
        this.blockElementAura.forEach((key, value) -> this.elementBias.compute(key, (ignored, current) -> {
            double next = (current == null ? 0.0D : current) - value;
            return Math.abs(next) < 1.0E-9D ? null : next;
        }));
        this.blockElementAura.clear();
        elements.forEach((holder, value) -> {
            double valid = finite(value);
            this.blockElementAura.put(holder, valid);
            this.elementBias.merge(holder, valid, Double::sum);
        });
        this.blockAura = newAura;
        this.blockRegenPerTick = newRegen;
        this.blockAuraKinds.clear();
        this.blockAuraKinds.addAll(tags);
        this.refreshAuraKinds();
    }

    public boolean hasAuraKinds(Collection<Identifier> values) {
        return this.auraKinds.containsAll(values);
    }

    private void refreshAuraKinds() {
        this.auraKinds.clear();
        this.auraKinds.addAll(this.templateAuraKinds);
        this.auraKinds.addAll(this.blockAuraKinds);
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
