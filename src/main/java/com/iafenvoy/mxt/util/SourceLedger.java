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
import java.util.Set;

/**
 * Who keeps a key alive: a key exists while at least one source holds it, one source can be added or dropped on
 * its own, and one source's whole contribution can be reconciled against what that source wants right now.
 * <p>
 * Ability grants and curse instances are the two users, which is why this is a shared type rather than a field of
 * either one: the rule "removing one source cannot remove another source's key" has to mean the same thing in
 * both, and so does "the last source leaving is what removes it".
 * <p>
 * Iteration order is the order keys were first held, which keeps saving, syncing and anything built from a
 * snapshot stable across a session.
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

    /**
     * Whether any key is held by that source, which is how a whole source's contribution is found again.
     */
    public boolean heldBy(Identifier source) {
        return this.sources.values().stream().anyMatch(values -> values.contains(source));
    }

    /**
     * Every key one source holds, which is what reconciling that source's contribution needs.
     */
    public Set<K> keysHeldBy(Identifier source) {
        Set<K> keys = new LinkedHashSet<>();
        this.sources.forEach((key, values) -> {
            if (values.contains(source)) keys.add(key);
        });
        return keys;
    }

    /**
     * Every source that holds anything at all, which is how a source whose gear is gone is found again.
     */
    public Set<Identifier> allSources() {
        Set<Identifier> all = new LinkedHashSet<>();
        this.sources.values().forEach(all::addAll);
        return all;
    }

    public Set<Map.Entry<K, Identifier>> entries() {
        Set<Map.Entry<K, Identifier>> entries = new LinkedHashSet<>();
        this.sources.forEach((key, values) -> values.forEach(source -> entries.add(Map.entry(key, source))));
        return entries;
    }

    public int size() {
        return this.sources.size();
    }

    /**
     * Adds one source. Returns whether it was not already holding that key.
     */
    public boolean grant(K key, Identifier source) {
        return this.sources.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(source);
    }

    /**
     * Drops one source. Returns whether it was holding the key at all; the caller asks {@link #holds} to find out
     * whether that was the last source.
     */
    public boolean revoke(K key, Identifier source) {
        Set<Identifier> values = this.sources.get(key);
        if (values == null || !values.remove(source)) return false;
        if (values.isEmpty()) this.sources.remove(key);
        return true;
    }

    /**
     * Drops every source of one key, for a removal that is not a release by one of them.
     */
    public boolean drop(K key) {
        return this.sources.remove(key) != null;
    }

    /**
     * Replaces one source's whole contribution: everything it used to hold and no longer wants is released, and
     * everything it wants and does not hold yet is granted. Returns whether anything changed.
     */
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

    /**
     * An independent copy, for detached drafts that must not touch the real ledger.
     */
    public SourceLedger<K> copy() {
        return new SourceLedger<>(this.snapshot());
    }

    /**
     * The persisted shape: one entry per key, in the order the keys were first held.
     */
    public Map<K, List<Identifier>> snapshot() {
        Map<K, List<Identifier>> snapshot = new LinkedHashMap<>();
        this.sources.forEach((key, values) -> snapshot.put(key, List.copyOf(values)));
        return snapshot;
    }
}
