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
 * The state one family keeps for the content its owner holds, addressed by the holder's id and the kind's class
 * (the host is the attachment this lives in). Values are stored as the kind instance itself, so the holder hands
 * back exactly what was written and never has to know any family's shape; the tick of every write is recorded
 * here, and a write marks the owning attachment dirty, which is what carries it into the save and the client.
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

    public void set(Identifier id, DataStorage value, long gameTime) {
        this.values.put(new Address(id, value.getClass()), new StoredData(value, gameTime));
        this.markDirty();
    }

    public boolean clear(Identifier id) {
        if (!this.values.keySet().removeIf(address -> address.id().equals(id))) return false;
        this.markDirty();
        return true;
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
