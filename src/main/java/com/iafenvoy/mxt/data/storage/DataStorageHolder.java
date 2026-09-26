package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * The state one family keeps for the content its owner holds, addressed by the holder's id and the kind's class
 * (the host is the attachment this lives in). One pair addresses exactly one value, so a write of a kind that is
 * already there replaces it instead of joining it. Values are stored as the kind instance itself, so the holder
 * hands back exactly what was written and never has to know any family's shape; the tick of every write is
 * recorded here, and a write marks the owning attachment dirty, which is what carries it into the save and the
 * client.
 */
public final class DataStorageHolder {
    public static final Codec<DataStorageHolder> CODEC = Entry.CODEC.listOf()
            .validate(DataStorageHolder::validate)
            .xmap(DataStorageHolder::new, DataStorageHolder::entries);
    private final Map<Address, StoredData> values = new LinkedHashMap<>();
    private ShouldSyncAttachment owner;

    public DataStorageHolder() {
    }

    private DataStorageHolder(List<Entry> entries) {
        for (Entry entry : entries) this.values.put(entry.address(), entry.data());
    }

    // Two entries claiming the same (id, kind) are malformed input rather than a second slot: the map could only
    // keep one of them, and picking a winner silently is how saved bytes and the value they decode to drift.
    private static DataResult<List<Entry>> validate(List<Entry> entries) {
        Set<Address> seen = new HashSet<>();
        for (Entry entry : entries) {
            Address address = entry.address();
            if (!seen.add(address))
                return DataResult.error(() -> "Duplicate " + DataStorage.name(entry.data().value())
                        + " stored for " + address.id());
        }
        return DataResult.success(entries);
    }

    // A detached holder (a draft) has no owner and is therefore silent.
    public void ownedBy(ShouldSyncAttachment owner) {
        this.owner = owner;
    }

    // A stored value of the wrong kind reads as "nothing stored" rather than being handed back.
    public <T extends DataStorage> Optional<T> get(Identifier id, Class<T> kind) {
        StoredData stored = this.values.get(new Address(id, kind));
        if (stored == null || !kind.isInstance(stored.value())) return Optional.empty();
        return Optional.of(kind.cast(stored.value()));
    }

    public long changedAt(Identifier id, Class<? extends DataStorage> kind) {
        StoredData stored = this.values.get(new Address(id, kind));
        return stored == null ? -1L : stored.changedAt();
    }

    // A second write of the same (id, kind) replaces the first, which is what keeps the pair a single slot.
    public void set(Identifier id, DataStorage value, long gameTime) {
        this.values.put(new Address(id, value.getClass()), new StoredData(value, gameTime));
        this.markDirty();
    }

    public boolean clear(Identifier id) {
        if (!this.values.keySet().removeIf(address -> address.id().equals(id))) return false;
        this.markDirty();
        return true;
    }

    // An item stack holds this as a value, so it is handed a new holder rather than a mutated one: a component
    // changed in place looks unchanged to the game's own comparison and to every copy of that stack.
    public DataStorageHolder with(Identifier id, DataStorage value, long gameTime) {
        DataStorageHolder next = this.copy();
        next.values.put(new Address(id, value.getClass()), new StoredData(value, gameTime));
        return next;
    }

    // How many values of one kind this host keeps, which is what a status line counts.
    public int count(Class<? extends DataStorage> kind) {
        return (int) this.values.keySet().stream().filter(address -> address.kind() == kind).count();
    }

    // An item stack compares a component by value, so the entries are this value's identity; the owner is a
    // transient back-pointer to the attachment and takes no part in it.
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof DataStorageHolder holder && this.values.equals(holder.values));
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    public DataStorageHolder copy() {
        return new DataStorageHolder(this.entries());
    }

    private void markDirty() {
        if (this.owner != null) this.owner.markDirty();
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        this.values.forEach((address, data) -> entries.add(new Entry(address.id(), data)));
        return List.copyOf(entries);
    }

    // The host is the attachment this holder lives in, so it does not have to be recorded.
    private record Address(Identifier id, Class<? extends DataStorage> kind) {
    }

    private record Entry(Identifier id, StoredData data) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                StoredData.CODEC.fieldOf("data").forGetter(Entry::data)
        ).apply(i, Entry::new));

        private Address address() {
            return new Address(this.id, this.data.value().getClass());
        }
    }
}
