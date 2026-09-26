package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.data.ability.AbilityContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Consumer;

/**
 * The state one family keeps for the content its owner holds, addressed by the holder's id and the kind's class
 * (the host is the attachment this lives in). One pair addresses exactly one value, so a second value of a kind
 * already there replaces it instead of joining it. Values are mutable and are stored as the kind instance itself,
 * so the holder hands back exactly what was written and never has to know any family's shape. Nothing here reaches
 * the attachment that holds it: each value records its own change, and whoever wants to save and sync reads that.
 */
public final class DataStorageHolder {
    public static final Codec<DataStorageHolder> CODEC = Entry.CODEC.listOf()
            .validate(DataStorageHolder::validate)
            .xmap(DataStorageHolder::new, DataStorageHolder::entries);
    private final Map<Address, DataStorage> values = new LinkedHashMap<>();

    public DataStorageHolder() {
    }

    private DataStorageHolder(List<Entry> entries) {
        for (Entry entry : entries) this.put(entry.address().id(), entry.data());
    }

    // Two entries claiming the same (id, kind) are malformed input rather than a second slot: the map could only
    // keep one of them, and picking a winner silently is how saved bytes and the value they decode to drift.
    private static DataResult<List<Entry>> validate(List<Entry> entries) {
        Set<Address> seen = new HashSet<>();
        for (Entry entry : entries) {
            Address address = entry.address();
            if (!seen.add(address))
                return DataResult.error(() -> "Duplicate " + DataStorage.name(entry.data())
                        + " stored for " + address.id());
        }
        return DataResult.success(entries);
    }

    // A stored value of the wrong kind reads as "nothing stored" rather than being handed back.
    public <T extends DataStorage> Optional<T> get(Identifier id, Class<T> kind) {
        DataStorage stored = this.values.get(new Address(id, kind));
        if (!kind.isInstance(stored)) return Optional.empty();
        return Optional.of(kind.cast(stored));
    }

    // A second write of the same (id, kind) replaces the first, which is what keeps the pair a single slot. Runtime
    // code mutates the value it got back instead of calling this.
    public void set(Identifier id, DataStorage value, long gameTime) {
        value.writtenAt(gameTime);
        this.put(id, value);
        // A write is a change like any other, so it is recorded on the value for the reader to find.
        value.markDirty();
    }

    // Removing is a change too, and the caller that asked for it is the one that knows; see AbilityAttachment.revoke.
    public boolean clear(Identifier id) {
        return this.values.keySet().removeIf(address -> address.id().equals(id));
    }

    // An item stack holds this as a value, so it is handed a new holder rather than a mutated one: a component
    // changed in place looks unchanged to the game's own comparison and to every copy of that stack.
    public DataStorageHolder with(Identifier id, DataStorage value) {
        DataStorageHolder next = this.copy();
        next.put(id, value);
        return next;
    }

    // How many values of one kind this host keeps, which is what a status line counts.
    public int count(Class<? extends DataStorage> kind) {
        return (int) this.values.keySet().stream().filter(address -> address.kind() == kind).count();
    }

    // Every value stored under one host id, which is the set the tick loop hands the current tick to.
    public void valuesOf(Identifier id, Consumer<DataStorage> consumer) {
        this.values.forEach((address, value) -> {
            if (address.id().equals(id)) consumer.accept(value);
        });
    }

    public void tick(Identifier id, AbilityContext context) {
        this.valuesOf(id, value -> value.tick(context));
    }

    // Consumes one host's change flags and answers whether any of them changed, which is what the main tick loop
    // reads to decide whether the attachment has to be saved and synced. `|` rather than `||`: every flag is read.
    public boolean isDirty(Identifier id) {
        boolean dirty = false;
        for (Map.Entry<Address, DataStorage> entry : this.values.entrySet())
            if (entry.getKey().id().equals(id)) dirty |= entry.getValue().isDirty();
        return dirty;
    }

    // An item stack compares a component by value, so the entries are this value's identity.
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof DataStorageHolder holder && this.values.equals(holder.values));
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    public DataStorageHolder copy() {
        DataStorageHolder next = new DataStorageHolder();
        this.values.forEach((address, value) -> next.put(address.id(), value.copy()));
        return next;
    }

    private void put(Identifier id, DataStorage value) {
        this.values.put(new Address(id, value.getClass()), value);
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        this.values.forEach((address, data) -> entries.add(new Entry(address.id(), data)));
        return List.copyOf(entries);
    }

    // The host is the attachment this holder lives in, so it does not have to be recorded.
    private record Address(Identifier id, Class<? extends DataStorage> kind) {
    }

    private record Entry(Identifier id, DataStorage data) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                DataStorage.CODEC.fieldOf("value").forGetter(Entry::data)
        ).apply(i, Entry::new));

        private Address address() {
            return new Address(this.id, this.data.getClass());
        }
    }
}
