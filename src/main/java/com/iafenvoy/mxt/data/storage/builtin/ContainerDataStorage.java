package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What a carrier's own container holds under the ability that declares its slot count. The list stays at the
 * declared capacity and an empty stack is a real value, so the codec must stay {@code ItemStack.OPTIONAL_CODEC}. */
public record ContainerDataStorage(List<ItemStack> contents) implements DataStorage {
    public static final MapCodec<ContainerDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(ContainerDataStorage::contents)
    ).apply(i, ContainerDataStorage::new));

    public ContainerDataStorage {
        contents = contents.stream().map(ItemStack::copy).toList();
    }

    @Override
    public MapCodec<ContainerDataStorage> codec() {
        return CODEC;
    }

    public ItemStack get(int slot) {
        return slot < 0 || slot >= this.contents.size() ? ItemStack.EMPTY : this.contents.get(slot);
    }

    public ContainerDataStorage with(int slot, ItemStack value, int capacity) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Container slot outside its capacity");
        ArrayList<ItemStack> values = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++)
            values.add(index < this.contents.size() ? this.contents.get(index).copy() : ItemStack.EMPTY);
        values.set(slot, value.copy());
        return new ContainerDataStorage(values);
    }

    // What a screen writes back after a click: one update instead of one per slot, at the full capacity.
    public static ContainerDataStorage of(int capacity, List<ItemStack> contents) {
        if (capacity < 0) throw new IllegalArgumentException("Container capacity cannot be negative");
        ArrayList<ItemStack> values = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++)
            values.add(index < contents.size() ? contents.get(index).copy() : ItemStack.EMPTY);
        return new ContainerDataStorage(values);
    }
}
