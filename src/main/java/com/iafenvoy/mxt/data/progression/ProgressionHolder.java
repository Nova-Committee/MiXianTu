package com.iafenvoy.mxt.data.progression;

import com.iafenvoy.mxt.runtime.DatapackCache;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.iafenvoy.mxt.util.chain.Chain;
import com.iafenvoy.mxt.util.chain.ChainCache;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * How far one body has climbed the progressions of one sort of owner, keyed by the owner's own holder rather than by
 * its id: the sort is part of the address, so a technique and a creature profile that share an id are two owners with
 * a slot each, and each sort is kept by the attachment of its own module. Only a level that was reached is stored,
 * so an owner whose entry level a data pack moves takes everyone who never advanced with it, and stepping back onto
 * the entry level drops the record again.
 */
public final class ProgressionHolder<T extends ProgressionOwner> extends AbstractChangeTrackable {
    private final Map<Holder<T>, Holder<Progression>> levels;

    public ProgressionHolder() {
        this(new LinkedHashMap<>());
    }

    // A decoded holder must never be shared between two bodies, so whoever adopts one copies it; the two attachments
    // that keep a sort do this in their constructors.
    public ProgressionHolder(ProgressionHolder<T> source) {
        this(new LinkedHashMap<>(source.levels));
    }

    private ProgressionHolder(Map<Holder<T>, Holder<Progression>> levels) {
        this.levels = levels;
    }

    // The key codec is the sort's own, so a holder only ever decodes owners of the one registry it belongs to.
    public static <T extends ProgressionOwner> Codec<ProgressionHolder<T>> codec(Codec<Holder<T>> owners) {
        return CollectionCodecs.map(owners, Progression.CODEC).xmap(values -> new ProgressionHolder<>(new LinkedHashMap<>(values)), holder -> holder.levels);
    }

    public Map<Holder<T>, Holder<Progression>> levels() {
        return this.levels;
    }

    public boolean isEmpty() {
        return this.levels.isEmpty();
    }

    // The level reached, empty while the holder still stands on that owner's entry level.
    public Optional<Holder<Progression>> recorded(Holder<T> owner) {
        return Optional.ofNullable(this.levels.get(owner));
    }

    // The record, or the owner's entry level while it never advanced; empty when the owner declares no chain.
    public Optional<Holder<Progression>> current(Holder<T> owner) {
        Holder<Progression> stored = this.levels.get(owner);
        return stored != null ? Optional.of(stored) : owner.value().entryLevel();
    }

    public Optional<Holder<Progression>> next(Holder<T> owner) {
        return this.current(owner).flatMap(holder -> holder.value().nextLevel());
    }

    // Below the entry level is not part of the owner's climb, so only a record can step down.
    public Optional<Holder<Progression>> previous(Holder<T> owner) {
        Holder<Progression> stored = this.levels.get(owner);
        return stored == null ? Optional.empty() : this.chains().previous(stored);
    }

    // One step up; false at the top of the chain and for an owner that declares none.
    public boolean promote(Holder<T> owner) {
        Holder<Progression> target = this.next(owner).orElse(null);
        return target != null && this.set(owner, target);
    }

    // One step down. The entry level is the absence of a record, so stepping onto it clears the slot.
    public boolean demote(Holder<T> owner) {
        Holder<Progression> from = this.levels.get(owner);
        return from != null && this.set(owner, this.chains().previous(from).orElse(null));
    }

    // Whether the owner reaches this level at all, which is what an administrative write asks before storing one.
    public boolean follows(Holder<T> owner, Holder<Progression> level) {
        return this.rank(owner, level) >= 0;
    }

    /**
     * Where a level sits above the owner's entry level (0 is the entry itself), or -1 when the owner never reaches
     * it. The validated chains answer while a server is running; a client, or anything asking before a world is
     * loaded, has no chain to answer from.
     */
    public int rank(Holder<T> owner, Holder<Progression> level) {
        Holder<Progression> entry = owner.value().entryLevel().orElse(null);
        if (entry == null || level == null) return -1;
        Chain<Progression> chain = this.chains().chainOf(entry).orElse(null);
        if (chain == null) return -1;
        int from = chain.indexOf(entry);
        int at = chain.indexOf(level);
        return at < from ? -1 : at - from;
    }

    // How many levels the owner's climb has, counted from its entry level up; zero for an owner that declares none
    // and for a chain the server refused.
    public int levelCount(Holder<T> owner) {
        Holder<Progression> entry = owner.value().entryLevel().orElse(null);
        if (entry == null) return 0;
        Chain<Progression> chain = this.chains().chainOf(entry).orElse(null);
        return chain == null ? 0 : chain.size() - chain.indexOf(entry);
    }

    // Writes where the owner stands, either way. A null level and the owner's own entry level both mean "no record",
    // since only levels that were reached are stored.
    public boolean set(Holder<T> owner, @Nullable Holder<Progression> level) {
        if (owner == null) return false;
        if (level == null || owner.value().entryLevel().filter(level::equals).isPresent()) return this.clear(owner);
        Holder<Progression> previous = this.levels.put(owner, level);
        if (level.equals(previous)) return false;
        this.markDirty();
        return true;
    }

    public boolean clear(Holder<T> owner) {
        if (owner == null || this.levels.remove(owner) == null) return false;
        this.markDirty();
        return true;
    }

    // True when a record the owner no longer reaches from its own entry level was dropped; the caller counts and
    // logs, since only it knows which body the record belonged to.
    public boolean prune(Holder<T> owner) {
        Holder<Progression> stored = this.levels.get(owner);
        return stored != null && !this.follows(owner, stored) && this.clear(owner);
    }

    public void clear() {
        if (this.levels.isEmpty()) return;
        this.levels.clear();
        this.markDirty();
    }

    public void setLevels(Map<Holder<T>, Holder<Progression>> values) {
        this.levels.clear();
        this.levels.putAll(values);
        this.markDirty();
    }

    // Drops every record for owners the caller no longer keeps, which is how a body that gave something up stops
    // standing on a level it cannot read.
    public void retainOnly(Iterable<Holder<T>> owners) {
        Set<Holder<T>> kept = new HashSet<>();
        for (Holder<T> owner : owners) kept.add(owner);
        if (this.levels.keySet().removeIf(owner -> !kept.contains(owner))) this.markDirty();
    }

    private ChainCache<Progression> chains() {
        return DatapackCache.get().progressionChains();
    }

    // An optional field is written by comparing the value to the default it was given, so an empty holder has to
    // compare equal to another empty one.
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof ProgressionHolder<?> holder && this.levels.equals(holder.levels));
    }

    @Override
    public int hashCode() {
        return this.levels.hashCode();
    }
}
