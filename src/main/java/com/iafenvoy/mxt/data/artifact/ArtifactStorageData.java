package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent server-owned contents of a storage artifact. Slot limits come from its archetype, never from this payload.
 */
public record ArtifactStorageData(List<ItemStack> contents) {
    public static final Codec<ArtifactStorageData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(ArtifactStorageData::contents)
    ).apply(instance, ArtifactStorageData::new));

    public ArtifactStorageData {
        contents = contents.stream().map(ItemStack::copy).toList();
    }

    public ItemStack get(int slot) {
        return slot < 0 || slot >= this.contents.size() ? ItemStack.EMPTY : this.contents.get(slot).copy();
    }

    public ArtifactStorageData with(int slot, ItemStack value, int capacity) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Storage slot outside artifact capacity");
        ArrayList<ItemStack> values = new ArrayList<>(Math.min(capacity, Math.max(this.contents.size(), slot + 1)));
        for (int index = 0; index < capacity; index++)
            values.add(index < this.contents.size() ? this.contents.get(index).copy() : ItemStack.EMPTY);
        values.set(slot, value.copy());
        return new ArtifactStorageData(values);
    }
}
