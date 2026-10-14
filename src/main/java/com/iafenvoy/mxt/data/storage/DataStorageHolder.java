package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityContext;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.*;
import java.util.function.Consumer;

/**
 * The state one module keeps for the content its owner holds, addressed by the ability's holder and the kind's class
 * (the host is the attachment this lives in). One pair addresses exactly one value, so a second value of a kind
 * already there replaces it instead of joining it. Values are mutable and are stored as the kind instance itself,
 * so the holder hands back exactly what was written and never has to know any module's shape. Nothing here reaches
 * the attachment that holds it: each value records its own change, and whoever wants to save and sync reads that.
 *
 * <p>The address is the ability holder, so a saved value whose definition left the pack is dropped on load (one
 * logged line) rather than kept under an id nothing can ask for again.
 */
public final class DataStorageHolder {
    public static final Codec<DataStorageHolder> CODEC = CollectionCodecs.list(Entry.CODEC).validate(DataStorageHolder::validate).xmap(DataStorageHolder::new, DataStorageHolder::entries);
    private final Map<Address, DataStorage> values = new LinkedHashMap<>();

    public DataStorageHolder() {
    }

    private DataStorageHolder(List<Entry> entries) {
        for (Entry entry : entries) this.put(entry.ability(), entry.data());
    }

    // Two entries claiming the same (ability, kind) are malformed input rather than a second slot: the map could only
    // keep one of them, and picking a winner silently is how saved bytes and the value they decode to drift.
    private static DataResult<List<Entry>> validate(List<Entry> entries) {
        Set<Address> seen = new HashSet<>();
        for (Entry entry : entries) {
            Address address = entry.address();
            if (!seen.add(address))
                return DataResult.error(() -> "Duplicate " + DataStorage.name(entry.data())
                        + " stored for " + address.abilityId());
        }
        return DataResult.success(entries);
    }

    // A stored value of the wrong kind reads as "nothing stored" rather than being handed back.
    public <T extends DataStorage> Optional<T> get(Holder<Ability> ability, Class<T> kind) {
        DataStorage stored = this.values.get(new Address(ability, kind));
        if (!kind.isInstance(stored)) return Optional.empty();
        return Optional.of(kind.cast(stored));
    }

    // A second write of the same (ability, kind) replaces the first, which is what keeps the pair a single slot.
    // Runtime code mutates the value it got back instead of calling this.
    public void set(Holder<Ability> ability, DataStorage value, long gameTime) {
        value.writtenAt(gameTime);
        this.put(ability, value);
        // A write is a change like any other, so it is recorded on the value for the reader to find.
        value.markDirty();
    }

    // Removing is a change too, and the caller that asked for it is the one that knows; see AbilityAttachment.revoke.
    public boolean clear(Holder<Ability> ability) {
        return this.values.keySet().removeIf(address -> address.ability().equals(ability));
    }

    // Clearing one host's state except the kinds that outlive it: an ability that loses its last source forgets its
    // charges and its pending cast, while the cooldown it was paid with keeps running, so taking the item away and
    // putting it back cannot buy the press again. A kind is addressed by its exact class, so this compares identity.
    public boolean clearExcept(Holder<Ability> ability, Class<? extends DataStorage> kept) {
        return this.values.keySet().removeIf(address -> address.ability().equals(ability) && address.kind() != kept);
    }

    // An item stack holds this as a value, so it is handed a new holder rather than a mutated one: a component
    // changed in place looks unchanged to the game's own comparison and to every copy of that stack.
    public DataStorageHolder with(Holder<Ability> ability, DataStorage value) {
        DataStorageHolder next = this.copy();
        next.put(ability, value);
        return next;
    }

    // How many values of one kind this host keeps, which is what a status line counts.
    public int count(Class<? extends DataStorage> kind) {
        return (int) this.values.keySet().stream().filter(address -> address.kind() == kind).count();
    }

    // Every value stored under one host, which is the set the tick loop hands the current tick to.
    public void valuesOf(Holder<Ability> ability, Consumer<DataStorage> consumer) {
        this.values.forEach((address, value) -> {
            if (address.ability().equals(ability)) consumer.accept(value);
        });
    }

    public void tick(Holder<Ability> ability, AbilityContext context) {
        this.valuesOf(ability, value -> value.tick(context));
    }

    // Consumes one host's change flags and answers whether any of them changed, which is what the main tick loop
    // reads to decide whether the attachment has to be saved and synced. `|` rather than `||`: every flag is read.
    public boolean isDirty(Holder<Ability> ability) {
        boolean dirty = false;
        for (Map.Entry<Address, DataStorage> entry : this.values.entrySet())
            if (entry.getKey().ability().equals(ability)) dirty |= entry.getValue().isDirty();
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
        this.values.forEach((address, value) -> next.put(address.ability(), value.copy()));
        return next;
    }

    private void put(Holder<Ability> ability, DataStorage value) {
        this.values.put(new Address(ability, value.getClass()), value);
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        this.values.forEach((address, data) -> entries.add(new Entry(address.ability(), data)));
        return List.copyOf(entries);
    }

    // The host is the attachment this holder lives in, so it does not have to be recorded.
    private record Address(Holder<Ability> ability, Class<? extends DataStorage> kind) {
        private String abilityId() {
            return this.ability.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
        }
    }

    private record Entry(Holder<Ability> ability, DataStorage data) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ability.CODEC.fieldOf("id").forGetter(Entry::ability),
                DataStorage.CODEC.fieldOf("value").forGetter(Entry::data)
        ).apply(i, Entry::new));

        private Address address() {
            return new Address(this.ability, this.data.getClass());
        }
    }
}
