package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.resource.Resource;
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
import net.minecraft.resources.Identifier;

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
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("aura_kinds", Set.of()).forGetter(AuraChunkAttachment::auraKinds),
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("template_aura_kinds", Set.of()).forGetter(AuraChunkAttachment::templateAuraKinds),
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("block_aura_kinds", Set.of()).forGetter(AuraChunkAttachment::blockAuraKinds),
            CollectionCodecs.intObjectMap(BlockAuraSectionCache.CODEC).optionalFieldOf("block_aura_sections", new Int2ObjectOpenHashMap<>()).forGetter(AuraChunkAttachment::blockAuraSections),
            AuraPool.GROUPED_CODEC.optionalFieldOf("aura", Map.of()).forGetter(AuraChunkAttachment::auras)
    ).apply(i, AuraChunkAttachment::new));
    private boolean initialized;
    private Optional<Holder<AuraZone>> template;
    private final Set<Identifier> auraKinds, templateAuraKinds, blockAuraKinds;
    private final Map<Holder<Resource>, AuraValue> blockAura;
    private final Int2ObjectMap<BlockAuraSectionCache> blockAuraSections;
    private final Map<Holder<Resource>, AuraPool> auras;
    /**
     * Runtime-only number of players whose bounded aura query includes a section.
     */
    private final Map<SectionPos, Integer> auraVisitors = new LinkedHashMap<>();

    public AuraChunkAttachment() {
        this(false, Optional.empty(), Set.of(), Set.of(), Set.of(), new Int2ObjectOpenHashMap<>(), Map.of());
    }

    private AuraChunkAttachment(boolean initialized, Optional<Holder<AuraZone>> template, Set<Identifier> auraKinds,
                                Set<Identifier> templateAuraKinds, Set<Identifier> blockAuraKinds,
                                Int2ObjectMap<BlockAuraSectionCache> blockAuraSections, Map<Holder<Resource>, AuraPool> auras) {
        this.initialized = initialized;
        this.template = template;
        this.auraKinds = new LinkedHashSet<>(auraKinds);
        this.templateAuraKinds = new LinkedHashSet<>(templateAuraKinds.isEmpty() ? auraKinds : templateAuraKinds);
        this.blockAuraKinds = new LinkedHashSet<>(blockAuraKinds);
        this.blockAuraSections = new Int2ObjectOpenHashMap<>(blockAuraSections);
        this.blockAura = aggregate(this.blockAuraSections);
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

    public Set<Identifier> auraKinds() {
        return this.auraKinds;
    }

    private Set<Identifier> templateAuraKinds() {
        return this.templateAuraKinds;
    }

    private Set<Identifier> blockAuraKinds() {
        return this.blockAuraKinds;
    }

    public Map<Holder<Resource>, AuraValue> blockAura() {
        return this.blockAura;
    }

    public Int2ObjectMap<BlockAuraSectionCache> blockAuraSections() {
        return this.blockAuraSections;
    }

    public Map<Holder<Resource>, AuraPool> auras() {
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

    public boolean hasAuraKinds(Collection<Identifier> values) {
        return this.auraKinds.containsAll(values);
    }

    public void initializeAuras(Map<Holder<Resource>, AuraPool> values, Collection<Identifier> kinds) {
        this.auras.clear();
        this.auras.putAll(values);
        this.templateAuraKinds.clear();
        this.templateAuraKinds.addAll(kinds);
        this.applyBlockContribution(Map.of(), this.blockAura);
        this.refreshAuraKinds();
        this.initialized = true;
    }

    /**
     * Atomically consumes all requested resource pools.
     */
    public boolean consume(Map<Holder<Resource>, Double> costs) {
        for (Entry<Holder<Resource>, Double> entry : costs.entrySet()) {
            double cost = entry.getValue();
            AuraPool pool = this.auras.get(entry.getKey());
            if (!Double.isFinite(cost) || cost < 0.0D || pool == null || pool.amount() < cost) return false;
        }
        costs.forEach((resource, cost) -> this.auras.computeIfPresent(resource, (ignored, pool) -> pool.change(-cost)));
        return true;
    }

    /**
     * Adds or removes resource aura while respecting the pool's own maximum.
     */
    public void change(Map<Holder<Resource>, Double> amounts) {
        amounts.forEach((resource, amount) -> {
            if (Double.isFinite(amount)) this.auras.computeIfPresent(resource, (ignored, pool) -> pool.change(amount));
        });
    }

    public void regenerateAuras(long elapsedTicks) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        this.auras.replaceAll((resource, pool) -> pool.change(pool.regenPerTick() * elapsedTicks));
    }

    /**
     * Replaces cached block contribution while retaining the already-consumed
     * portion of every affected resource.
     */
    public void setBlockContribution(List<BlockAuraContribution> sources, Collection<Identifier> kinds) {
        Map<Holder<Resource>, AuraValue> previous = new LinkedHashMap<>(this.blockAura);
        this.blockAuraSections.clear();
        Map<Integer, List<BlockAuraContribution>> grouped = new LinkedHashMap<>();
        sources.forEach(source -> grouped.computeIfAbsent(SectionPos.blockToSectionCoord(source.position().getY()), ignored -> new LinkedList<>()).add(source));
        grouped.forEach((sectionY, values) -> this.blockAuraSections.put(sectionY, new BlockAuraSectionCache(aggregate(values), values)));
        Map<Holder<Resource>, AuraValue> values = aggregate(this.blockAuraSections);
        this.blockAura.clear();
        this.blockAura.putAll(values);
        if (this.initialized) this.applyBlockContribution(previous, this.blockAura);
        this.blockAuraKinds.clear();
        this.blockAuraKinds.addAll(kinds);
        this.refreshAuraKinds();
    }

    /**
     * Invalidates the per-subsection emitter details while retaining the
     * aggregate as the baseline for an immediate rebuild.
     */
    public void clearBlockAuraCache() {
        this.blockAuraSections.clear();
    }

    private static Map<Holder<Resource>, AuraValue> aggregate(List<BlockAuraContribution> sources) {
        Map<Holder<Resource>, AuraValue> result = new LinkedHashMap<>();
        sources.forEach(source -> source.aura().forEach((resource, value) ->
                result.merge(resource, value, AuraChunkAttachment::merge)));
        return result;
    }

    private static Map<Holder<Resource>, AuraValue> aggregate(Int2ObjectMap<BlockAuraSectionCache> sections) {
        Map<Holder<Resource>, AuraValue> result = new LinkedHashMap<>();
        sections.values().forEach(section -> section.aura().forEach((resource, value) ->
                result.merge(resource, value, AuraChunkAttachment::merge)));
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

    private void applyBlockContribution(Map<Holder<Resource>, AuraValue> previous, Map<Holder<Resource>, AuraValue> current) {
        Set<Holder<Resource>> resources = new LinkedHashSet<>(previous.keySet());
        resources.addAll(current.keySet());
        for (Holder<Resource> resource : resources) {
            AuraValue oldValue = previous.getOrDefault(resource, AuraValue.ZERO);
            AuraValue newValue = current.getOrDefault(resource, AuraValue.ZERO);
            AuraPool pool = this.auras.get(resource);
            if (pool == null && newValue != AuraValue.ZERO) {
                this.auras.put(resource, new AuraPool(newValue.amount(), newValue.max().resolve(newValue.amount()), newValue.regenPerTick()));
                continue;
            }
            if (pool == null) continue;
            double oldMaximum = oldValue.max().resolve(oldValue.amount());
            double newMaximum = newValue.max().resolve(newValue.amount());
            double maximum = addMaximum(pool.maximum(), newMaximum - oldMaximum);
            double amount = Math.max(0.0D, pool.amount() + newValue.amount() - oldValue.amount());
            this.auras.put(resource, new AuraPool(amount, maximum,
                    pool.regenPerTick() + newValue.regenPerTick() - oldValue.regenPerTick()));
        }
    }

    private static double addMaximum(double maximum, double delta) {
        return maximum == Double.POSITIVE_INFINITY ? maximum : Math.max(0.0D, maximum + delta);
    }

    private void refreshAuraKinds() {
        this.auraKinds.clear();
        this.auraKinds.addAll(this.templateAuraKinds);
        this.auraKinds.addAll(this.blockAuraKinds);
    }
}
