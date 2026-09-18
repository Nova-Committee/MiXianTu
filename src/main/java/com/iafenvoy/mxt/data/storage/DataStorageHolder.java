package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The state one family keeps for the content its owner holds: the values are addressed by the holder's id and the
 * kind's class, because the attachment this lives in is already the host. Whoever keeps state owns one of these
 * inside its own attachment, so a family's values are saved and synced with the content that owns them instead of
 * in a store every family shares.
 *
 * <p>What is stored is the kind instance itself, so the holder hands back exactly what was written and never has
 * to know any family's shape, and a value is decoded by its own {@code type} dispatch, so saved data records the
 * holder's id and nothing else. The tick of every write is recorded here, since that is the one thing every kind
 * would otherwise have to keep for itself. A write tells the owning attachment it is dirty, which is what carries
 * the value into the save and to the client.</p>
 */
public final class DataStorageHolder {
    public static final Codec<DataStorageHolder> CODEC = Entry.CODEC.listOf()
            .xmap(DataStorageHolder::new, DataStorageHolder::entries);
    private final Map<Address, StoredData> values = new LinkedHashMap<>();
    private ShouldSyncAttachment owner;

    public DataStorageHolder() {
    }

    private DataStorageHolder(List<Entry> entries) {
        for (Entry entry : entries) this.values.put(entry.address(), entry.data());
    }

    /**
     * Hands this holder the attachment it belongs to: from now on every write marks that attachment dirty, so a
     * value that changes is saved and synced with its host. A detached holder — a draft — has no owner and is
     * therefore silent.
     */
    public void ownedBy(ShouldSyncAttachment owner) {
        this.owner = owner;
    }

    /**
     * Reads one kind's value; empty when nothing is stored for it, or when the stored value is not that kind —
     * a content change between two loads reads as "nothing stored" instead of handing back something else.
     */
    public <T extends DataStorage> Optional<T> get(Identifier id, Class<T> kind) {
        StoredData stored = this.values.get(new Address(id, kind));
        if (stored == null || !kind.isInstance(stored.value())) return Optional.empty();
        return Optional.of(kind.cast(stored.value()));
    }

    /**
     * The tick of the last write to one kind, or {@code -1} when nothing is stored.
     */
    public long changedAt(Identifier id, Class<? extends DataStorage> kind) {
        StoredData stored = this.values.get(new Address(id, kind));
        return stored == null ? -1L : stored.changedAt();
    }

    /**
     * Writes one value; the value's own class is the slot it goes into.
     */
    public void set(Identifier id, DataStorage value, long gameTime) {
        this.values.put(new Address(id, value.getClass()), new StoredData(value, gameTime));
        this.markDirty();
    }

    /**
     * Drops everything one holder id owns, which is what revoking that content does.
     */
    public boolean clear(Identifier id) {
        if (!this.values.keySet().removeIf(address -> address.id().equals(id))) return false;
        this.markDirty();
        return true;
    }

    /**
     * A detached copy, for a caller that validates a whole sequence of writes before committing it.
     */
    public DataStorageHolder copy() {
        return new DataStorageHolder(this.entries());
    }

    private void markDirty() {
        if (this.owner != null) this.owner.markDirty();
    }

    /**
     * The flat form the codec writes.
     */
    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        this.values.forEach((address, data) -> entries.add(new Entry(address.id(), data)));
        return List.copyOf(entries);
    }

    /**
     * One in-memory address: the holder's id and the kind's class. The host is the attachment this holder lives
     * in, so it does not have to be recorded.
     */
    private record Address(Identifier id, Class<? extends DataStorage> kind) {
    }

    /**
     * One stored value in the form the codec writes.
     */
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
