package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative chunk-local aura stock. Every value is independently keyed by
 * its element; there is deliberately no aggregate aura pool.
 */
public final class AuraChunkComponent {
    public static final MapCodec<AuraChunkComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(AuraChunkComponent::initialized),
            AuraZone.CODEC.optionalFieldOf("template").forGetter(AuraChunkComponent::template),
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("aura_kinds", Set.of()).forGetter(AuraChunkComponent::auraKinds),
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("template_aura_kinds", Set.of()).forGetter(AuraChunkComponent::templateAuraKinds),
            AuraValue.MAP_CODEC.optionalFieldOf("block_aura", Map.of()).forGetter(AuraChunkComponent::blockAura),
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("block_aura_kinds", Set.of()).forGetter(AuraChunkComponent::blockAuraKinds),
            AuraPool.MAP_CODEC.optionalFieldOf("aura", Map.of()).forGetter(AuraChunkComponent::auras)
    ).apply(i, AuraChunkComponent::new));
    private boolean initialized;
    private Optional<Holder<AuraZone>> template;
    private final Set<Identifier> auraKinds;
    private final Set<Identifier> templateAuraKinds;
    private final Map<Holder<Element>, AuraValue> blockAura;
    private final Set<Identifier> blockAuraKinds;
    private final Map<Holder<Element>, AuraPool> auras;

    public AuraChunkComponent() {
        this(false, Optional.empty(), Set.of(), Set.of(), Map.of(), Set.of(), Map.of());
    }

    private AuraChunkComponent(boolean initialized, Optional<Holder<AuraZone>> template, Set<Identifier> auraKinds,
                               Set<Identifier> templateAuraKinds, Map<Holder<Element>, AuraValue> blockAura,
                               Set<Identifier> blockAuraKinds, Map<Holder<Element>, AuraPool> auras) {
        this.initialized = initialized;
        this.template = template;
        this.auraKinds = new LinkedHashSet<>(auraKinds);
        this.templateAuraKinds = new LinkedHashSet<>(templateAuraKinds.isEmpty() ? auraKinds : templateAuraKinds);
        this.blockAura = new LinkedHashMap<>(blockAura);
        this.blockAuraKinds = new LinkedHashSet<>(blockAuraKinds);
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

    public Map<Holder<Element>, AuraValue> blockAura() {
        return this.blockAura;
    }

    private Set<Identifier> blockAuraKinds() {
        return this.blockAuraKinds;
    }

    public Map<Holder<Element>, AuraPool> auras() {
        return this.auras;
    }

    public boolean hasAuraKinds(Collection<Identifier> values) {
        return this.auraKinds.containsAll(values);
    }

    public void initializeAuras(Map<Holder<Element>, AuraPool> values, Collection<Identifier> kinds) {
        this.auras.clear();
        this.auras.putAll(values);
        this.templateAuraKinds.clear();
        this.templateAuraKinds.addAll(kinds);
        this.applyBlockContribution(Map.of(), this.blockAura);
        this.refreshAuraKinds();
        this.initialized = true;
    }

    /**
     * Atomically consumes all requested elemental pools.
     */
    public boolean consume(Map<Holder<Element>, Double> costs) {
        for (Entry<Holder<Element>, Double> entry : costs.entrySet()) {
            double cost = entry.getValue();
            AuraPool pool = this.auras.get(entry.getKey());
            if (!Double.isFinite(cost) || cost < 0.0D || pool == null || pool.amount() < cost) return false;
        }
        costs.forEach((element, cost) -> this.auras.computeIfPresent(element, (ignored, pool) -> pool.change(-cost)));
        return true;
    }

    /**
     * Adds or removes elemental aura while respecting the pool's own maximum.
     */
    public void change(Map<Holder<Element>, Double> amounts) {
        amounts.forEach((element, amount) -> {
            if (Double.isFinite(amount)) this.auras.computeIfPresent(element, (ignored, pool) -> pool.change(amount));
        });
    }

    public void regenerateAuras(long elapsedTicks) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        this.auras.replaceAll((element, pool) -> pool.change(pool.regenPerTick() * elapsedTicks));
    }

    /**
     * Replaces cached block contribution while retaining the already-consumed
     * portion of every affected element.
     */
    public void setBlockContribution(Map<Holder<Element>, AuraValue> values, Collection<Identifier> kinds) {
        Map<Holder<Element>, AuraValue> previous = new LinkedHashMap<>(this.blockAura);
        this.blockAura.clear();
        this.blockAura.putAll(values);
        if (this.initialized) this.applyBlockContribution(previous, this.blockAura);
        this.blockAuraKinds.clear();
        this.blockAuraKinds.addAll(kinds);
        this.refreshAuraKinds();
    }

    private void applyBlockContribution(Map<Holder<Element>, AuraValue> previous, Map<Holder<Element>, AuraValue> current) {
        Set<Holder<Element>> elements = new LinkedHashSet<>(previous.keySet());
        elements.addAll(current.keySet());
        for (Holder<Element> element : elements) {
            AuraValue oldValue = previous.getOrDefault(element, AuraValue.ZERO);
            AuraValue newValue = current.getOrDefault(element, AuraValue.ZERO);
            AuraPool pool = this.auras.get(element);
            if (pool == null && newValue != AuraValue.ZERO) {
                this.auras.put(element, new AuraPool(newValue.amount(), newValue.max().resolve(newValue.amount()), newValue.regenPerTick()));
                continue;
            }
            if (pool == null) continue;
            double oldMaximum = oldValue.max().resolve(oldValue.amount());
            double newMaximum = newValue.max().resolve(newValue.amount());
            double maximum = addMaximum(pool.maximum(), newMaximum - oldMaximum);
            double amount = Math.max(0.0D, pool.amount() + newValue.amount() - oldValue.amount());
            this.auras.put(element, new AuraPool(amount, maximum,
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
