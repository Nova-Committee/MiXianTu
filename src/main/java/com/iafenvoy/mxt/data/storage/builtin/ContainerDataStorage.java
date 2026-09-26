package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What a carrier's own container holds under the ability that declares its slot count. The list stays at the
 * declared capacity and an empty stack is a real value, so the codec must stay {@code ItemStack.OPTIONAL_CODEC}. */
public final class ContainerDataStorage extends DataStorage {
    // The declaration entry the storage type lists this kind by; the contents are the runtime's.
    public static final ContainerDataStorage INSTANCE = new ContainerDataStorage(List.of());
    public static final MapCodec<ContainerDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(ContainerDataStorage::contents)
    ).apply(i, ContainerDataStorage::new));
    private final List<ItemStack> contents = new ArrayList<>();

    private ContainerDataStorage(List<ItemStack> contents) {
        contents.forEach(stack -> this.contents.add(stack.copy()));
    }

    // What a screen writes back after a click: one update instead of one per slot, at the full capacity.
    public static ContainerDataStorage of(int capacity, List<ItemStack> contents) {
        if (capacity < 0) throw new IllegalArgumentException("Container capacity cannot be negative");
        List<ItemStack> values = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++)
            values.add(index < contents.size() ? contents.get(index).copy() : ItemStack.EMPTY);
        return new ContainerDataStorage(values);
    }

    @Override
    public MapCodec<ContainerDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new ContainerDataStorage(this.contents);
    }

    public List<ItemStack> contents() {
        return List.copyOf(this.contents);
    }

    public ItemStack get(int slot) {
        return slot < 0 || slot >= this.contents.size() ? ItemStack.EMPTY : this.contents.get(slot);
    }

    // For a host whose storage is a value (an item stack's component): the copy is what keeps two stacks that share
    // a holder from seeing each other's changes.
    public ContainerDataStorage with(int slot, ItemStack value, int capacity) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Container slot outside its capacity");
        List<ItemStack> values = new ArrayList<>(this.contents);
        while (values.size() < capacity) values.add(ItemStack.EMPTY);
        values.set(slot, value);
        return new ContainerDataStorage(values);
    }

    public void set(int slot, ItemStack value, int capacity) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Container slot outside its capacity");
        while (this.contents.size() < capacity) this.contents.add(ItemStack.EMPTY);
        this.contents.set(slot, value.copy());
        this.markDirty();
    }

    public void fill(int capacity, List<ItemStack> values) {
        if (capacity < 0) throw new IllegalArgumentException("Container capacity cannot be negative");
        this.contents.clear();
        for (int index = 0; index < capacity; index++)
            this.contents.add(index < values.size() ? values.get(index).copy() : ItemStack.EMPTY);
        this.markDirty();
    }
}
