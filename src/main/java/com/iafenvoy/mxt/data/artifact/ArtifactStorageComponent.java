package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Persistent server-owned contents of a storage artifact. Slot limits come from its archetype, never from this payload.
 */
public record ArtifactStorageComponent(List<ItemStack> contents) {
    public static final Codec<ArtifactStorageComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(ArtifactStorageComponent::contents)
    ).apply(i, ArtifactStorageComponent::new));

    public ArtifactStorageComponent {
        contents = new LinkedList<>(contents.stream().map(ItemStack::copy).toList());
    }

    public ItemStack get(int slot) {
        return slot < 0 || slot >= this.contents.size() ? ItemStack.EMPTY : this.contents.get(slot);
    }

    public ArtifactStorageComponent with(int slot, ItemStack value, int capacity) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Storage slot outside artifact capacity");
        ArrayList<ItemStack> values = new ArrayList<>(Math.min(capacity, Math.max(this.contents.size(), slot + 1)));
        for (int index = 0; index < capacity; index++)
            values.add(index < this.contents.size() ? this.contents.get(index).copy() : ItemStack.EMPTY);
        values.set(slot, value.copy());
        return new ArtifactStorageComponent(values);
    }
}
