package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Who keeps a key alive: a key exists while at least one source holds it, and one source's whole contribution can
 * be reconciled against what that source wants now. Shared by ability grants and curse instances so that rule
 * means the same thing in both. Iteration order is first-held order, which keeps snapshots stable.
 */
public final class SourceLedger<K> {
    private final Map<K, Set<Identifier>> sources = new LinkedHashMap<>();

    public SourceLedger() {
    }

    public SourceLedger(Map<K, List<Identifier>> snapshot) {
        snapshot.forEach((key, values) -> this.sources.put(key, new LinkedHashSet<>(values)));
    }

    public static <K> Codec<SourceLedger<K>> codec(Codec<K> keyCodec) {
        return CollectionCodecs.map(keyCodec, Identifier.CODEC.listOf()).xmap(SourceLedger::new, SourceLedger::snapshot);
    }

    public Set<K> keys() {
        return Set.copyOf(this.sources.keySet());
    }

    public Set<Identifier> of(K key) {
        Set<Identifier> values = this.sources.get(key);
        return values == null ? Set.of() : Set.copyOf(values);
    }

    public boolean holds(K key) {
        return this.sources.containsKey(key);
    }

    public boolean holds(K key, Identifier source) {
        Set<Identifier> values = this.sources.get(key);
        return values != null && values.contains(source);
    }

    public boolean heldBy(Identifier source) {
        return this.sources.values().stream().anyMatch(values -> values.contains(source));
    }

    public Set<K> keysHeldBy(Identifier source) {
        Set<K> keys = new LinkedHashSet<>();
        this.sources.forEach((key, values) -> {
            if (values.contains(source)) keys.add(key);
        });
        return keys;
    }

    public Set<Identifier> allSources() {
        Set<Identifier> all = new LinkedHashSet<>();
        this.sources.values().forEach(all::addAll);
        return all;
    }

    public Set<Entry<K, Identifier>> entries() {
        Set<Entry<K, Identifier>> entries = new LinkedHashSet<>();
        this.sources.forEach((key, values) -> values.forEach(source -> entries.add(Map.entry(key, source))));
        return entries;
    }

    public int size() {
        return this.sources.size();
    }

    // Returns whether the source was added, i.e. it was not already holding that key.
    public boolean grant(K key, Identifier source) {
        return this.sources.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(source);
    }

    // Returns whether it was holding the key; the caller asks {@link #holds} whether that was the last source.
    public boolean revoke(K key, Identifier source) {
        Set<Identifier> values = this.sources.get(key);
        if (values == null || !values.remove(source)) return false;
        if (values.isEmpty()) this.sources.remove(key);
        return true;
    }

    public boolean drop(K key) {
        return this.sources.remove(key) != null;
    }

    // Replaces one source's whole contribution: release what it no longer wants, grant what it wants and lacks.
    public boolean reconcile(Identifier source, Collection<K> desired) {
        Set<K> wanted = new LinkedHashSet<>(desired);
        List<K> previous = new ArrayList<>();
        this.sources.forEach((key, values) -> {
            if (values.contains(source)) previous.add(key);
        });
        boolean changed = false;
        for (K key : previous) if (!wanted.contains(key)) changed |= this.revoke(key, source);
        for (K key : wanted) if (!previous.contains(key)) changed |= this.grant(key, source);
        return changed;
    }

    // For detached drafts that must not touch the real ledger.
    public SourceLedger<K> copy() {
        return new SourceLedger<>(this.snapshot());
    }

    public Map<K, List<Identifier>> snapshot() {
        Map<K, List<Identifier>> snapshot = new LinkedHashMap<>();
        this.sources.forEach((key, values) -> snapshot.put(key, List.copyOf(values)));
        return snapshot;
    }
}
