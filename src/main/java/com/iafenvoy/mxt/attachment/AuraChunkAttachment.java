package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.BlockAuraContribution;
import com.iafenvoy.mxt.runtime.world.BlockAuraSectionCache;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;

import java.util.*;
import java.util.Map.Entry;

/**
 * Authoritative chunk-local aura stock. Every value is independently keyed by
 * its resource; there is deliberately no aggregate aura pool.
 */
public final class AuraChunkAttachment {
    public static final MapCodec<AuraChunkAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(AuraChunkAttachment::initialized),
            AuraZone.CODEC.optionalFieldOf("template").forGetter(AuraChunkAttachment::template),
            CollectionCodecs.intObjectMap(BlockAuraSectionCache.CODEC).optionalFieldOf("block_aura_sections", new Int2ObjectOpenHashMap<>()).forGetter(AuraChunkAttachment::blockAuraSections),
            AuraValue.MAP_CODEC.optionalFieldOf("absorbed_aura", Map.of()).forGetter(AuraChunkAttachment::absorbedAura),
            AuraPool.GROUPED_CODEC.optionalFieldOf("aura", Map.of()).forGetter(AuraChunkAttachment::auras)
    ).apply(i, AuraChunkAttachment::new));
    private boolean initialized;
    private Optional<Holder<AuraZone>> template;
    private final Map<Holder<Aura>, AuraValue> blockAura;
    private final Map<Holder<Aura>, AuraValue> absorbedAura;
    private final Int2ObjectMap<BlockAuraSectionCache> blockAuraSections;
    private final Map<Holder<Aura>, AuraPool> auras;
    /**
     * Runtime-only; never saved.
     */
    private final Map<SectionPos, Integer> auraVisitors = new LinkedHashMap<>();

    public AuraChunkAttachment() {
        this(false, Optional.empty(), new Int2ObjectOpenHashMap<>(), Map.of(), Map.of());
    }

    private AuraChunkAttachment(boolean initialized, Optional<Holder<AuraZone>> template,
                                Int2ObjectMap<BlockAuraSectionCache> blockAuraSections, Map<Holder<Aura>, AuraValue> absorbedAura,
                                Map<Holder<Aura>, AuraPool> auras) {
        this.initialized = initialized;
        this.template = template;
        this.blockAuraSections = new Int2ObjectOpenHashMap<>(blockAuraSections);
        this.blockAura = aggregate(this.blockAuraSections);
        this.absorbedAura = new LinkedHashMap<>(absorbedAura);
        this.auras = new LinkedHashMap<>(auras);
    }

    public boolean initialized() {
        return this.initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public Optional<Holder<AuraZone>> template() {
        return this.template;
    }

    public void setTemplate(Optional<Holder<AuraZone>> template) {
        this.template = template;
    }

    public Map<Holder<Aura>, AuraValue> blockAura() {
        return this.blockAura;
    }

    public Int2ObjectMap<BlockAuraSectionCache> blockAuraSections() {
        return this.blockAuraSections;
    }

    public Map<Holder<Aura>, AuraPool> auras() {
        return this.auras;
    }

    public int auraVisitors(SectionPos section) {
        return Math.max(0, this.auraVisitors.getOrDefault(section, 0));
    }

    public Map<SectionPos, Integer> auraVisitors() {
        return this.auraVisitors;
    }

    public void clearAuraVisitors() {
        this.auraVisitors.clear();
    }

    public void addAuraVisitor(SectionPos section) {
        this.auraVisitors.merge(section, 1, Integer::sum);
    }

    public void initializeAuras(Map<Holder<Aura>, AuraPool> values) {
        this.auras.clear();
        this.auras.putAll(values);
        this.applyBlockContribution(Map.of(), this.blockAura);
        this.initialized = true;
    }

    /**
     * Atomically consumes all requested aura pools.
     */
    public boolean consume(Map<Holder<Aura>, Double> costs) {
        for (Entry<Holder<Aura>, Double> entry : costs.entrySet()) {
            double cost = entry.getValue();
            AuraPool pool = this.auras.get(entry.getKey());
            if (!Double.isFinite(cost) || cost < 0.0D || pool == null || pool.amount() < cost) return false;
        }
        costs.forEach((aura, cost) -> this.auras.computeIfPresent(aura, (ignored, pool) -> pool.change(-cost)));
        return true;
    }

    /**
     * Adds or removes aura while respecting the pool's own maximum.
     */
    public void change(Map<Holder<Aura>, Double> amounts) {
        amounts.forEach((aura, amount) -> {
            if (Double.isFinite(amount)) this.auras.computeIfPresent(aura, (ignored, pool) -> pool.change(amount));
        });
    }

    public void regenerateAuras(long elapsedTicks) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        this.auras.replaceAll((aura, pool) -> pool.change(pool.regenPerTick() * elapsedTicks));
    }

    /**
     * Replaces the cached block contribution while retaining the already-consumed portion of every
     * affected aura. {@link BlockAuraContribution#absorbed()} emitters stay out of the shared stock
     * and the per-section caches: the environment subtracts this chunk's aggregate from the pool, so
     * leaving them in would hand the same aura back to every query.
     */
    public void setBlockContribution(List<BlockAuraContribution> sources) {
        Map<Holder<Aura>, AuraValue> previous = new LinkedHashMap<>(this.blockAura);
        this.blockAuraSections.clear();
        Map<Integer, List<BlockAuraContribution>> grouped = new LinkedHashMap<>();
        sources.forEach(source -> grouped.computeIfAbsent(SectionPos.blockToSectionCoord(source.position().getY()), ignored -> new LinkedList<>()).add(source));
        // The primitive key type picks the primitive put; the deprecated Integer overload from Map would box.
        grouped.forEach((sectionY, values) -> {
            List<BlockAuraContribution> environment = values.stream().filter(value -> !value.absorbed()).toList();
            if (environment.isEmpty()) return;
            this.blockAuraSections.put(sectionY.intValue(), new BlockAuraSectionCache(aggregate(environment), environment));
        });
        Map<Holder<Aura>, AuraValue> values = aggregate(this.blockAuraSections);
        this.blockAura.clear();
        this.blockAura.putAll(values);
        Map<Holder<Aura>, AuraValue> absorbed = aggregate(sources.stream()
                .filter(BlockAuraContribution::absorbed).toList());
        this.absorbedAura.clear();
        this.absorbedAura.putAll(absorbed);
        if (this.initialized) this.applyBlockContribution(previous, this.blockAura);
    }

    /**
     * Totals of the emitters that stand inside a formation, which the formation spends instead of the
     * environment.
     */
    public Map<Holder<Aura>, AuraValue> absorbedAura() {
        return this.absorbedAura;
    }

    /**
     * Invalidates the per-subsection emitter details while retaining the
     * aggregate as the baseline for an immediate rebuild.
     */
    public void clearBlockAuraCache() {
        this.blockAuraSections.clear();
    }

    private static Map<Holder<Aura>, AuraValue> aggregate(List<BlockAuraContribution> sources) {
        Map<Holder<Aura>, AuraValue> result = new LinkedHashMap<>();
        sources.forEach(source -> source.aura().forEach((aura, value) ->
                result.merge(aura, value, AuraChunkAttachment::merge)));
        return result;
    }

    private static Map<Holder<Aura>, AuraValue> aggregate(Int2ObjectMap<BlockAuraSectionCache> sections) {
        Map<Holder<Aura>, AuraValue> result = new LinkedHashMap<>();
        sections.values().forEach(section -> section.aura().forEach((aura, value) ->
                result.merge(aura, value, AuraChunkAttachment::merge)));
        return result;
    }

    private static AuraValue merge(AuraValue first, AuraValue second) {
        double amount = first.amount() + second.amount();
        double maximum = first.max().resolve(first.amount()) + second.max().resolve(second.amount());
        double firstWeight = Math.max(0.0D, first.amount());
        double secondWeight = Math.max(0.0D, second.amount());
        double totalWeight = firstWeight + secondWeight;
        int color = totalWeight <= 0.0D ? first.color() : weightedColor(first.color(), firstWeight, second.color(), secondWeight, totalWeight);
        return new AuraValue(amount, new Fixed(maximum),
                first.regenPerTick() + second.regenPerTick(), color);
    }

    private static int weightedColor(int first, double firstWeight, int second, double secondWeight, double totalWeight) {
        int red = (int) Math.round((((first >>> 16) & 0xFF) * firstWeight + ((second >>> 16) & 0xFF) * secondWeight) / totalWeight);
        int green = (int) Math.round((((first >>> 8) & 0xFF) * firstWeight + ((second >>> 8) & 0xFF) * secondWeight) / totalWeight);
        int blue = (int) Math.round(((first & 0xFF) * firstWeight + (second & 0xFF) * secondWeight) / totalWeight);
        return (red << 16) | (green << 8) | blue;
    }

    private void applyBlockContribution(Map<Holder<Aura>, AuraValue> previous, Map<Holder<Aura>, AuraValue> current) {
        Set<Holder<Aura>> auras = new LinkedHashSet<>(previous.keySet());
        auras.addAll(current.keySet());
        for (Holder<Aura> aura : auras) {
            AuraValue oldValue = previous.getOrDefault(aura, AuraValue.ZERO);
            AuraValue newValue = current.getOrDefault(aura, AuraValue.ZERO);
            AuraPool pool = this.auras.get(aura);
            if (pool == null && newValue != AuraValue.ZERO) {
                this.auras.put(aura, AuraPool.natural(newValue.amount(), newValue.max().resolve(newValue.amount()), newValue.regenPerTick()));
                continue;
            }
            if (pool == null) continue;
            double oldMaximum = oldValue.max().resolve(oldValue.amount());
            double newMaximum = newValue.max().resolve(newValue.amount());
            double maximum = addMaximum(pool.maximum(), newMaximum - oldMaximum);
            double amount = Math.max(0.0D, pool.amount() + newValue.amount() - oldValue.amount());
            this.auras.put(aura, AuraPool.natural(amount, maximum,
                    pool.regenPerTick() + newValue.regenPerTick() - oldValue.regenPerTick()));
        }
    }

    private static double addMaximum(double maximum, double delta) {
        return maximum == Double.POSITIVE_INFINITY ? maximum : Math.max(0.0D, maximum + delta);
    }

}
