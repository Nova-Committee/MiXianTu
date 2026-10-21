package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.builtin.FixedAuraMaximum;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.BlockAuraSectionCache;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Authoritative aura stock of one chunk, held per sub-chunk: every value is independently keyed by its resource,
 * and every section carries its own amount, ceiling and regeneration. There is deliberately no whole-chunk pool.
 */
public final class AuraChunkAttachment {
    public static final MapCodec<AuraChunkAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AuraZone.CODEC.lenientOptionalFieldOf("template").forGetter(x -> x.template),
            CollectionCodecs.intObjectMap(BlockAuraSectionCache.CODEC).lenientOptionalFieldOf("block_aura_sections", new Int2ObjectOpenHashMap<>()).forGetter(AuraChunkAttachment::blockAuraSections),
            CollectionCodecs.intObjectMap(AuraPool.GROUPED_CODEC).lenientOptionalFieldOf("aura", new Int2ObjectOpenHashMap<>()).forGetter(AuraChunkAttachment::auras)
    ).apply(i, AuraChunkAttachment::new));
    private Optional<Holder<AuraZone>> template;
    private final Int2ObjectMap<BlockAuraSectionCache> blockAuraSections;
    private final Int2ObjectMap<Map<Holder<Aura>, AuraPool>> auras;
    // Runtime-only; never saved.
    private final Map<SectionPos, Integer> auraVisitors = new LinkedHashMap<>();
    private long regenerationTick = -1L;

    public AuraChunkAttachment() {
        this(Optional.empty(), new Int2ObjectOpenHashMap<>(), new Int2ObjectOpenHashMap<>());
    }

    private AuraChunkAttachment(Optional<Holder<AuraZone>> template, Int2ObjectMap<BlockAuraSectionCache> blockAuraSections, Int2ObjectMap<Map<Holder<Aura>, AuraPool>> auras) {
        this.template = template;
        this.blockAuraSections = new Int2ObjectOpenHashMap<>(blockAuraSections);
        this.auras = new Int2ObjectOpenHashMap<>(auras);
    }

    // Seeding writes a map for every section, empty or not, so a section without one is one that was never
    // seeded - a zone that provides nothing is still distinguishable from a chunk nobody has looked at.
    public boolean seeded() {
        return !this.auras.isEmpty();
    }

    // A section without a pool map has never been seeded, which is also how a chunk saved before pools were split
    // is recognised: its old flat map does not decode, so the sections are missing and it is seeded again.
    public boolean ready(int sectionY) {
        return this.auras.containsKey(sectionY);
    }

    @Nullable
    public Holder<AuraZone> template() {
        return this.template.orElse(null);
    }

    public void setTemplate(Holder<AuraZone> template) {
        this.template = Optional.of(template);
    }

    public void clearTemplate() {
        this.template = Optional.empty();
    }

    public Int2ObjectMap<BlockAuraSectionCache> blockAuraSections() {
        return this.blockAuraSections;
    }

    public Map<Holder<Aura>, AuraValue> sectionAura(int sectionY) {
        BlockAuraSectionCache cache = this.blockAuraSections.get(sectionY);
        return cache == null ? Map.of() : cache.aura();
    }

    public Map<Holder<Aura>, AuraPool> pools(int sectionY) {
        return this.auras.getOrDefault(sectionY, Map.of());
    }

    public Int2ObjectMap<Map<Holder<Aura>, AuraPool>> auras() {
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
        this.changeAuraVisitors(section, 1);
    }

    public void changeAuraVisitors(SectionPos section, int delta) {
        int count = this.auraVisitors.getOrDefault(section, 0) + delta;
        if (count <= 0) this.auraVisitors.remove(section);
        else this.auraVisitors.put(section, count);
    }

    // The zone's stock is one chunk-sized amount, so it is split evenly across the sections: the world total stays
    // what the zone declares, and no section can be drained on behalf of another.
    public void initializeAuras(Map<Holder<Aura>, AuraPool> values, int minSectionY, int sectionCount) {
        this.regenerationTick = -1L;
        int sections = Math.max(1, sectionCount);
        this.auras.clear();
        for (int i = 0; i < sections; i++) {
            Map<Holder<Aura>, AuraPool> pools = new LinkedHashMap<>();
            values.forEach((aura, pool) -> pools.put(aura, pool.share(sections)));
            this.auras.put(minSectionY + i, pools);
        }
        for (Int2ObjectMap.Entry<BlockAuraSectionCache> entry : this.blockAuraSections.int2ObjectEntrySet())
            this.applyBlockDelta(entry.getIntKey(), Map.of(), entry.getValue().aura());
    }

    // All-or-nothing: every pool is checked before any is spent.
    public boolean consume(int sectionY, Map<Holder<Aura>, Double> costs) {
        if (!this.canConsume(sectionY, costs)) return false;
        Map<Holder<Aura>, AuraPool> pools = this.auras.get(sectionY);
        if (pools != null) costs.forEach((aura, cost) -> {
            AuraPool pool = pools.get(aura);
            if (pool != null) pool.change(-cost);
        });
        return true;
    }

    /**
     * Read-only availability test; {@link #consume} asks it again before spending anything.
     */
    public boolean canConsume(int sectionY, Map<Holder<Aura>, Double> costs) {
        Map<Holder<Aura>, AuraPool> pools = this.auras.get(sectionY);
        for (Map.Entry<Holder<Aura>, Double> entry : costs.entrySet()) {
            double cost = entry.getValue();
            AuraPool pool = pools == null ? null : pools.get(entry.getKey());
            if (!Double.isFinite(cost) || cost < 0.0D || pool == null || pool.amount() < cost) return false;
        }
        return true;
    }

    // Add or remove, clamped by the pool's own maximum.
    public void change(int sectionY, Map<Holder<Aura>, Double> amounts) {
        Map<Holder<Aura>, AuraPool> pools = this.auras.get(sectionY);
        if (pools == null) return;
        amounts.forEach((aura, amount) -> {
            AuraPool pool = pools.get(aura);
            if (pool != null && Double.isFinite(amount)) pool.change(amount);
        });
    }

    public void regenerateAuras(long elapsedTicks) {
        this.regenerate(elapsedTicks);
    }

    public void startRegeneration(long now) {
        if (now < 0L) throw new IllegalArgumentException("Tick cannot be negative");
        this.regenerationTick = now;
    }

    public boolean regenerateAurasUntil(long now) {
        if (now < 0L) throw new IllegalArgumentException("Tick cannot be negative");
        long previous = this.regenerationTick;
        this.regenerationTick = now;
        return previous >= 0L && now > previous && this.regenerate(now - previous);
    }

    private boolean regenerate(long elapsedTicks) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        if (elapsedTicks == 0L) return false;
        boolean changed = false;
        for (Map<Holder<Aura>, AuraPool> pools : this.auras.values()) {
            for (AuraPool pool : pools.values()) {
                double current = pool.amount();
                double rate = pool.regenPerTick();
                if (rate == 0.0D || rate > 0.0D && current >= pool.maximum() || rate < 0.0D && current <= 0.0D) continue;
                double next = Math.clamp(current + rate * elapsedTicks, 0.0D, Math.min(Double.MAX_VALUE, pool.maximum()));
                if (next == current) continue;
                pool.change(next - current);
                changed = true;
            }
        }
        return changed;
    }

    // One section only: every other section's pools are left alone.
    public void setSectionAura(int sectionY, Map<Holder<Aura>, AuraValue> aura) {
        Map<Holder<Aura>, AuraValue> previous = this.sectionAura(sectionY);
        if (aura.isEmpty()) this.blockAuraSections.remove(sectionY);
        else this.blockAuraSections.put(sectionY, new BlockAuraSectionCache(aura));
        this.applyBlockContribution(sectionY, previous, aura);
    }

    // One block edited in place: only its own contribution leaves or arrives, never the whole section. The stored
    // aggregate stays exact, so the next full rebuild still diffs against the truth instead of double counting.
    public void changeBlock(int sectionY, Map<Holder<Aura>, AuraValue> departed, Map<Holder<Aura>, AuraValue> arrived) {
        if (departed.isEmpty() && arrived.isEmpty()) return;
        Map<Holder<Aura>, AuraValue> previous = this.sectionAura(sectionY);
        Map<Holder<Aura>, AuraValue> current = new LinkedHashMap<>(previous);
        departed.forEach((aura, value) -> {
            AuraValue left = removed(previous.getOrDefault(aura, AuraValue.ZERO), value);
            if (saturated(left)) current.remove(aura);
            else current.put(aura, left);
        });
        arrived.forEach((aura, value) -> current.merge(aura, value, AuraValue::merge));
        if (current.isEmpty()) this.blockAuraSections.remove(sectionY);
        else this.blockAuraSections.put(sectionY, new BlockAuraSectionCache(current));
        this.applyBlockContribution(sectionY, previous, current);
    }

    // A block leaves with exactly the contribution it brought, so adding and removing one block is reversible;
    // only the colour stays that of the block that left, which a full rebuild mixes again.
    private static AuraValue removed(AuraValue aggregate, AuraValue departed) {
        double maximum = Math.max(0.0D, aggregate.max().resolve(aggregate.amount()) - departed.max().resolve(departed.amount()));
        return new AuraValue(Math.max(0.0D, aggregate.amount() - departed.amount()), new FixedAuraMaximum(maximum),
                Math.max(0.0D, aggregate.regenPerTick() - departed.regenPerTick()), aggregate.color());
    }

    private static boolean saturated(AuraValue value) {
        return value.amount() <= 0.0D && value.regenPerTick() <= 0.0D && value.max().resolve(0.0D) <= 0.0D;
    }

    private void applyBlockContribution(int sectionY, Map<Holder<Aura>, AuraValue> previous, Map<Holder<Aura>, AuraValue> current) {
        if (!this.ready(sectionY)) return;
        this.applyBlockDelta(sectionY, previous, current);
    }

    private void applyBlockDelta(int sectionY, Map<Holder<Aura>, AuraValue> previous, Map<Holder<Aura>, AuraValue> current) {
        Map<Holder<Aura>, AuraPool> pools = this.auras.get(sectionY);
        if (pools == null) {
            if (current.isEmpty()) return;
            pools = new LinkedHashMap<>();
            this.auras.put(sectionY, pools);
        }
        Set<Holder<Aura>> auras = new LinkedHashSet<>(previous.keySet());
        auras.addAll(current.keySet());
        for (Holder<Aura> aura : auras) {
            AuraValue before = previous.getOrDefault(aura, AuraValue.ZERO);
            AuraValue after = current.getOrDefault(aura, AuraValue.ZERO);
            AuraPool pool = pools.get(aura);
            if (pool != null) {
                pool.update(before, after);
                continue;
            }
            if (after == AuraValue.ZERO) continue;
            pools.put(aura, AuraPool.of(before, after));
        }
    }
}
